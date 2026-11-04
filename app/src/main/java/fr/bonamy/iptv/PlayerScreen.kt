package fr.bonamy.iptv

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import fr.bonamy.iptv.core.Channel
import fr.bonamy.iptv.core.PlaybackRecovery
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

@OptIn(markerClass = [UnstableApi::class])
internal class PlayerScreen(
    private val activity: MainActivity,
    val channel: Channel,
    private val preferences: ViewerPreferences,
    initialStream: Int,
    private val renderersFactory: RenderersFactory? = null,
) : FrameLayout(activity) {
    var streamIndex = initialStream.coerceIn(channel.streams.indices)
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val recovery = PlaybackRecovery()
    private var player: ExoPlayer? = null
    private var paused = false
    private var failed = false
    private var recoverAtLiveEdge = false
    private var useSoftwareDecoder = false
    private var picker: AlertDialog? = null
    private var guideVisible = false
    private val normalFocusability = descendantFocusability
    private val normalAccessibility = importantForAccessibility
    private val video = PlayerView(context).apply {
        useController = false; isFocusable = false; setKeepContentOnPlayerReset(true)
        setShutterBackgroundColor(Color.BLACK)
    }
    private val controls = FrameLayout(context)
    private val message = context.label("Connecting…", 22f).apply { id = R.id.player_status; gravity = Gravity.CENTER }
    private val connecting = ConnectingView(context)
    private val retry = context.headerAction("Retry") { recovery.reset(); prepare() }.apply { visibility = View.GONE }
    private val playButton = PlayerControlButton(context, PlayerControlButton.Icon.PAUSE, "Pause").apply { setOnClickListener { togglePause() } }
    private val subtitlesButton = context.playerAction("Subtitles") { tracks(C.TRACK_TYPE_TEXT, "Subtitles") }
    private val live = context.label("LIVE", 13f).apply { bold(); gravity = Gravity.CENTER; isFocusable = false }
    private val favoriteButton = context.playerAction(favoriteTitle()) { preferences.toggle(channel); favoriteButtonText() }
    private val hide = Runnable { hideControls() }
    private val stable = Runnable { recovery.reset() }
    private val retryPlayback = Runnable {
        val engine = player
        if (recoverAtLiveEdge && engine != null) {
            failed = false; retry.visibility = View.GONE; message.visibility = View.GONE
            connecting.isError = false; connecting.visibility = View.VISIBLE
            engine.seekToDefaultPosition()
            engine.prepare(); engine.playWhenReady = !paused
        } else prepare()
    }

    init {
        setBackgroundColor(Color.BLACK); isFocusable = true; isFocusableInTouchMode = true
        addView(video, LayoutParams(-1, -1))
        addView(connecting, LayoutParams(context.dp(140), context.dp(140), Gravity.CENTER))
        addView(message, LayoutParams(-1, context.dp(42), Gravity.CENTER).apply { topMargin = context.dp(160) })
        val heading = context.row().apply {
            setBackgroundColor(0xcc000000.toInt()); setPadding(context.dp(16), 0, context.dp(16), 0)
        }
        heading.addView(context.label(channel.name, 24f, 0xffcccccc.toInt()).apply {
            bold(); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(subtitlesButton, LinearLayout.LayoutParams(-2, context.dp(48)))
        heading.addView(context.playerAction("Quality") { tracks(C.TRACK_TYPE_VIDEO, "Quality") }, LinearLayout.LayoutParams(-2, context.dp(48)))
        heading.addView(context.playerAction("Audio") { tracks(C.TRACK_TYPE_AUDIO, "Audio") }, LinearLayout.LayoutParams(-2, context.dp(48)))
        if (channel.streams.size > 1) heading.addView(context.playerAction("Source") { streams() }, LinearLayout.LayoutParams(-2, context.dp(48)))
        heading.addView(favoriteButton, LinearLayout.LayoutParams(-2, context.dp(48)))
        controls.addView(heading, LayoutParams(-1, context.dp(68), Gravity.TOP))
        val bottom = FrameLayout(context).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xd907111d.toInt(), Color.TRANSPARENT))
        }
        val transport = context.row()
        transport.addView(playButton, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
        transport.addView(live, LinearLayout.LayoutParams(context.dp(80), context.dp(40)).apply { marginStart = context.dp(12) })
        bottom.addView(transport, LayoutParams(-2, context.dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = context.dp(28) })
        controls.addView(bottom, LayoutParams(-1, context.dp(108), Gravity.BOTTOM))
        addView(controls, LayoutParams(-1, -1))
        addView(retry, LayoutParams(context.dp(100), context.dp(40), Gravity.CENTER).apply { topMargin = context.dp(235) })
        playButton.requestFocus()
    }

    fun start() {
        if (player != null) return
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prepare()
        post { showControls() }
    }
    private fun prepare() {
        handler.removeCallbacks(retryPlayback); handler.removeCallbacks(stable); handler.removeCallbacks(hide)
        player?.release(); player = null
        failed = false; recoverAtLiveEdge = false; retry.visibility = View.GONE
        message.text = ""; message.visibility = View.GONE
        connecting.isError = false; connecting.visibility = View.VISIBLE
        val stream = channel.streams[streamIndex]
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
            .setUserAgent(stream.headers["User-Agent"] ?: "Mozilla/5.0 IPTV/0.1")
            .setDefaultRequestProperties(stream.headers)
        val sources = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
        val segmentHeaders = stream.headers + ("User-Agent" to (stream.headers["User-Agent"] ?: "Mozilla/5.0 IPTV/0.1"))
        val hls = HlsMediaSource.Factory(HlsDataSourceFactory { type ->
            if (type == C.DATA_TYPE_MEDIA) ParallelMediaDataSource(http, segmentHeaders) else http.createDataSource()
        }).setPlaylistParserFactory(LivePlaylistParserFactory()).setExtractorFactory(DefaultHlsExtractorFactory(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES, true))
        val compatibleSources = object : MediaSource.Factory by sources {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource =
                if (Util.inferContentTypeForUriAndMimeType(mediaItem.localConfiguration!!.uri, mediaItem.localConfiguration!!.mimeType) == C.CONTENT_TYPE_HLS)
                    hls.createMediaSource(mediaItem) else sources.createMediaSource(mediaItem)
        }
        val decoders = renderersFactory ?: NextRenderersFactory(activity)
            .setExtensionRendererMode(if (useSoftwareDecoder) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(context).setRenderersFactory(decoders).setMediaSourceFactory(compatibleSources).build().also { engine ->
            video.player = engine
            engine.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (engine != player) return
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            message.text = ""; message.visibility = View.GONE
                            connecting.isError = false; connecting.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            message.visibility = View.GONE; connecting.visibility = View.GONE; retry.visibility = View.GONE
                            if (!paused) scheduleHide()
                        }
                        Player.STATE_ENDED -> failedPlayback()
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    handler.removeCallbacks(stable)
                    if (isPlaying) handler.postDelayed(stable, 15000)
                }
                override fun onPlayerError(error: PlaybackException) { if (engine == player) failedPlayback(error) }
            })
            engine.setMediaItem(MediaItem.fromUri(stream.url))
            engine.prepare(); engine.playWhenReady = !paused
        }
        updatePlayButton()
    }

    private fun failedPlayback(error: PlaybackException? = null) {
        if (failed) return
        recoverAtLiveEdge = error?.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
        val codecFallback = !useSoftwareDecoder && renderersFactory == null && error?.errorCode in setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ) && FfmpegLibrary.isAvailable()
        if (codecFallback) useSoftwareDecoder = true
        failed = true; handler.removeCallbacks(hide); handler.removeCallbacks(stable)
        val scheduled = recovery.nextDelayMs()
        val delay = if (codecFallback && scheduled != null) 0L else scheduled
        connecting.isError = delay == null; connecting.visibility = View.VISIBLE
        message.setTextColor(if (delay == null) 0xffed6673.toInt() else MUTED)
        message.text = if (delay != null) "Reconnecting…" else "Channel unavailable"
        message.visibility = View.VISIBLE
        if (delay != null) handler.postDelayed(retryPlayback, delay)
        else { retry.visibility = View.VISIBLE; showControls(); retry.requestFocus() }
    }

    private fun updatePlayButton() {
        playButton.icon = if (paused) PlayerControlButton.Icon.PLAY else PlayerControlButton.Icon.PAUSE
        playButton.contentDescription = if (paused) "Play" else "Pause"
    }
    private fun favoriteTitle() = if (channel.id in preferences.favorites()) "Unfavorite" else "Favorite"
    private fun favoriteButtonText() { favoriteButton.text = favoriteTitle(); scheduleHide() }
    private fun togglePause() {
        paused = !paused; player?.playWhenReady = !paused
        updatePlayButton()
        if (paused) handler.removeCallbacks(hide) else scheduleHide()
    }
    private fun streams() {
        handler.removeCallbacks(hide)
        val labels = channel.streams.mapIndexed { index, stream -> "${index + 1}. ${stream.label}" }.toTypedArray()
        picker = AlertDialog.Builder(context).setTitle("Streams").setSingleChoiceItems(labels, streamIndex) { dialog, index ->
            streamIndex = index; recovery.reset(); useSoftwareDecoder = false; paused = false; dialog.dismiss(); prepare()
        }.setNegativeButton("Cancel", null).create().also { it.setOnDismissListener { picker = null; scheduleHide() }; it.show() }
    }
    private fun tracks(type: Int, title: String) {
        val engine = player ?: return
        handler.removeCallbacks(hide)
        data class Track(val group: Tracks.Group, val index: Int, val label: String)
        val tracks = engine.currentTracks.groups.filter { it.type == type }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                val format = group.getTrackFormat(index)
                val language = format.language?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH) }
                Track(group, index, if (type == C.TRACK_TYPE_VIDEO && format.height > 0) "${format.height}p" else format.label ?: language ?: "Track ${index + 1}")
            }
        }
        val choices = listOf(if (type == C.TRACK_TYPE_TEXT) "Off" else "Auto") + tracks.map { it.label }
        val selected = tracks.indexOfFirst { it.group.isTrackSelected(it.index) } + 1
        picker = AlertDialog.Builder(context).setTitle(title).setSingleChoiceItems(choices.toTypedArray(), selected) { dialog, index ->
            val parameters = engine.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
                .setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT && index == 0)
            if (index > 0) tracks[index - 1].let { parameters.setOverrideForType(TrackSelectionOverride(it.group.mediaTrackGroup, it.index)) }
            engine.trackSelectionParameters = parameters.build(); dialog.dismiss()
        }.setNegativeButton("Cancel", null).create().also { it.setOnDismissListener { picker = null; scheduleHide() }; it.show() }
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hide)
        if (!guideVisible && !paused && !failed && picker == null && player?.playbackState == Player.STATE_READY) handler.postDelayed(hide, 6000)
    }
    private fun showControls() { if (guideVisible) return; controls.visibility = View.VISIBLE; playButton.requestFocusFromTouch(); scheduleHide() }
    private fun hideControls() { controls.visibility = View.GONE; requestFocus() }
    fun setGuideVisible(visible: Boolean) {
        guideVisible = visible
        descendantFocusability = if (visible) FOCUS_BLOCK_DESCENDANTS else normalFocusability
        importantForAccessibility = if (visible) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else normalAccessibility
        if (visible) { picker?.dismiss(); handler.removeCallbacks(hide); hideControls() } else requestFocus()
    }
    fun back(): Boolean {
        if (controls.visibility == View.VISIBLE) { handler.removeCallbacks(hide); hideControls(); return true }
        return false
    }
    fun handleKey(key: Int): Boolean {
        if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { togglePause(); return true }
        if (guideVisible) return false
        if (key !in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) return false
        if (controls.visibility != View.VISIBLE) { showControls(); if (key == KeyEvent.KEYCODE_DPAD_UP) subtitlesButton.requestFocus(); return true }
        scheduleHide(); return false
    }
    fun stop() {
        handler.removeCallbacksAndMessages(null); picker?.dismiss(); picker = null
        video.player = null; player?.release(); player = null
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    fun release() { stop() }
}
