package fr.bonamy.iptv

import android.os.SystemClock
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.bonamy.iptv.core.Channel
import fr.bonamy.iptv.core.Stream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit local/emulator probe. Reports timing and formats, never stream URLs or headers. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class StreamStabilityTest {
    private fun player(view: android.view.View): ExoPlayer? {
        if (view is PlayerView) return view.player as? ExoPlayer
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) player(view.getChildAt(index))?.let { return it }
        return null
    }

    @Test fun sustainedFramesAndLiveWindow() = probe()

    internal fun probe(overrideChannel: Channel? = null, durationOverrideMs: Long? = null, startupLimitMs: Long = 30000) {
        val args = InstrumentationRegistry.getArguments()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val channel = overrideChannel ?: args.getString("streamUrl")?.let { url -> Channel("probe", "Local probe", null, emptyList(), listOf(Stream(url, "Main"))) }
            ?: LibraryStore(context).initial().channels.first { it.id == (args.getString("channelId") ?: "TF1.fr") }
        val durationMs = durationOverrideMs ?: (args.getString("sampleSeconds")?.toLongOrNull() ?: 60L).coerceIn(15, 180) * 1000
        val report = File(context.filesDir, "playback-trace.jsonl").apply { writeText("") }
        val began = SystemClock.elapsedRealtime()
        fun emit(kind: String, vararg values: Pair<String, Any?>) {
            val json = JSONObject().put("ms", SystemClock.elapsedRealtime() - began).put("event", kind)
            values.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
            synchronized(report) { report.appendText(json.toString() + "\n") }
        }
        var errors = 0
        val errorCodes = mutableListOf<Int>()
        var softwareVideo = false
        var audioRendered = false
        var audioSelected = false
        val allowDecoderFallback = args.getString("allowDecoderFallback") == "true"
        var recoveringDecoder = false
        var decoderFailedAt = 0L
        var decoderRecoveryGap = 0L
        var rendered = false
        var firstFrameAt = 0L
        var screen: PlayerScreen? = null
        var previousFrames = -1
        var latestFrame = began
        var worstGap = 0L
        val seen = mutableSetOf<ExoPlayer>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    if (args.getString("software") == "true") check(io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.isAvailable()) { "FFmpeg probe decoder did not load" }
                    screen = if (args.getString("software") == "true") PlayerScreen(activity, channel, ViewerPreferences(activity), 0,
                        io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory(activity)
                            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
                        else PlayerScreen(activity, channel, ViewerPreferences(activity), 0)
                    activity.addContentView(screen, FrameLayout.LayoutParams(-1, -1))
                    screen!!.start()
                }
                while (SystemClock.elapsedRealtime() - began < durationMs) {
                    scenario.onActivity {
                        val engine = player(screen!!) ?: return@onActivity
                        if (seen.add(engine)) {
                            previousFrames = -1
                            audioSelected = audioSelected || engine.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO)
                            emit("engine", "number" to seen.size)
                            if (args.getString("disableAudio") == "true") engine.trackSelectionParameters = engine.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                            engine.addAnalyticsListener(object : AnalyticsListener {
                                override fun onLoadStarted(time: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) {
                                    emit("loadStart", "task" to info.loadTaskId, "type" to data.dataType, "track" to data.trackType)
                                }
                                override fun onLoadCompleted(time: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) {
                                    emit("loadEnd", "task" to info.loadTaskId, "type" to data.dataType, "track" to data.trackType, "bytes" to info.bytesLoaded, "elapsed" to info.loadDurationMs)
                                }
                                override fun onTracksChanged(time: AnalyticsListener.EventTime, tracks: Tracks) {
                                    audioSelected = audioSelected || tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)
                                    tracks.groups.forEach { group -> (0 until group.length).forEach { index ->
                                        val format = group.getTrackFormat(index)
                                        emit("track", "mime" to format.sampleMimeType, "codec" to format.codecs, "selected" to group.isTrackSelected(index), "supported" to group.isTrackSupported(index))
                                    } }
                                }
                                override fun onVideoDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestamp: Long, duration: Long) { softwareVideo = softwareVideo || name.startsWith("ffmpeg"); emit("videoDecoder", "name" to name) }
                                override fun onAudioDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestamp: Long, duration: Long) { emit("audioDecoder", "name" to name) }
                                override fun onPlayerError(time: AnalyticsListener.EventTime, error: PlaybackException) {
                                    errors++; errorCodes.add(error.errorCode)
                                    recoveringDecoder = allowDecoderFallback && error.errorCode in decoderErrors
                                    if (recoveringDecoder) decoderFailedAt = SystemClock.elapsedRealtime()
                                    emit("error", "code" to error.errorCodeName)
                                }
                            })
                        }
                        engine.videoDecoderCounters?.ensureUpdated()
                        engine.audioDecoderCounters?.ensureUpdated()
                        val audioFrames = engine.audioDecoderCounters?.renderedOutputBufferCount ?: 0
                        audioRendered = audioRendered || audioFrames > 0
                        val frames = engine.videoDecoderCounters?.renderedOutputBufferCount ?: 0
                        if (frames > previousFrames && frames > 0) {
                            val now = SystemClock.elapsedRealtime()
                            if (!rendered) firstFrameAt = now - began
                            if (recoveringDecoder) decoderRecoveryGap = maxOf(decoderRecoveryGap, now - if (rendered) latestFrame else decoderFailedAt)
                            else if (rendered) worstGap = maxOf(worstGap, now - latestFrame)
                            recoveringDecoder = false
                            latestFrame = now; rendered = true
                        }
                        previousFrames = frames
                        val playlist = (engine.currentManifest as? HlsManifest)?.mediaPlaylist
                        val window = if (engine.currentTimeline.isEmpty) null else engine.currentTimeline.getWindow(engine.currentMediaItemIndex, androidx.media3.common.Timeline.Window())
                        emit("sample", "state" to engine.playbackState, "playing" to engine.isPlaying, "loading" to engine.isLoading,
                            "position" to engine.currentPosition, "buffered" to engine.totalBufferedDuration, "frames" to frames, "audioFrames" to audioFrames,
                            "dropped" to engine.videoDecoderCounters?.droppedBufferCount, "liveOffset" to engine.currentLiveOffset,
                            "defaultPosition" to window?.defaultPositionMs, "window" to window?.durationMs,
                            "targetDuration" to playlist?.targetDurationUs?.div(1000), "sequence" to playlist?.mediaSequence)
                    }
                    SystemClock.sleep(500)
                }
            } finally {
                scenario.onActivity { screen?.release() }
                emit("result", "rendered" to rendered, "audioRendered" to audioRendered, "errors" to errors,
                    "worstFrameGap" to worstGap, "decoderRecoveryGap" to decoderRecoveryGap, "startup" to firstFrameAt)
            }
        }
        assertTrue("No video frames rendered; see playback-trace.jsonl", rendered)
        assertTrue("Startup was too slow: $firstFrameAt ms; see playback-trace.jsonl", firstFrameAt < startupLimitMs)
        if (allowDecoderFallback) {
            assertTrue("Only one decoder failure may recover to FFmpeg", errors <= 1 && errorCodes.all { it in decoderErrors })
            if (errors > 0) assertTrue("Decoder failure did not recover through FFmpeg", softwareVideo)
            assertTrue("Decoder recovery exceeded ten seconds", decoderRecoveryGap < 10000)
        } else assertEquals("Player errors occurred; see playback-trace.jsonl", 0, errors)
        if (audioSelected && args.getString("disableAudio") != "true") assertTrue("Selected audio did not render", audioRendered)
        assertTrue("Video stopped advancing; see playback-trace.jsonl", SystemClock.elapsedRealtime() - latestFrame < 5000)
        assertTrue("Playback stalled; see playback-trace.jsonl", worstGap < 5000)
    }

    private val decoderErrors = setOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    )
}
