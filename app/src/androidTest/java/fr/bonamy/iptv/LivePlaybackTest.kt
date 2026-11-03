package fr.bonamy.iptv

import android.view.View
import android.view.ViewGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Live acceptance probe, run explicitly; availability depends on the public stream. */
@RunWith(AndroidJUnit4::class)
class LivePlaybackTest {
    private fun findPlayer(view: View): ExoPlayer? {
        if (view is PlayerView) return view.player as? ExoPlayer
        if (view is ViewGroup) for (index in 0 until view.childCount) findPlayer(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun streamPosition(player: ExoPlayer): Long =
        player.currentPosition + if (player.currentTimeline.isEmpty) 0 else
            player.currentTimeline.getWindow(player.currentMediaItemIndex, androidx.media3.common.Timeline.Window()).positionInFirstPeriodMs
    private fun waitUntil(scenario: ActivityScenario<MainActivity>, predicate: (MainActivity) -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 60000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var done = false
            scenario.onActivity { done = predicate(it) }
            if (done) return
            android.os.SystemClock.sleep(200)
        }
        throw AssertionError("Live playback condition did not settle")
    }
    @Test fun liveChannelRendersAdvancesAndReturnsToSearch() {
        val arguments = InstrumentationRegistry.getArguments()
        val query = arguments.getString("channelQuery") ?: "France 24"
        val channelId = arguments.getString("channelId") ?: "France24.fr"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { it.findViewById<android.widget.TextView>(R.id.browser_status).text.toString() != "Loading channels…" }
            val cache = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "library.json")
            val previousCacheTime = cache.lastModified()
            onView(withId(R.id.browser_menu)).perform(click())
            onView(withText("Refresh")).perform(click())
            onView(withId(R.id.search_button)).perform(click())
            onView(withId(R.id.search_input)).perform(replaceText(query), androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
            waitUntil(scenario) {
                val grid = it.findViewById<RecyclerView>(R.id.channel_grid)
                val channels = (grid.adapter as ChannelAdapter).channels
                grid.isShown && channels.any { channel -> channel.id == channelId }
            }
            var position = 0
            scenario.onActivity {
                position = (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.indexOfFirst { channel -> channel.id == channelId }
            }
            val selectedAt = android.os.SystemClock.elapsedRealtime()
            onView(withId(R.id.channel_grid)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(position, click()))
            scenario.onActivity { activity ->
                findPlayer(activity.findViewById(android.R.id.content))?.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onLoadCompleted(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, info: androidx.media3.exoplayer.source.LoadEventInfo, data: androidx.media3.exoplayer.source.MediaLoadData) {
                        android.util.Log.i("PlaybackProbe", "load type=${data.dataType} track=${data.trackType} bytes=${info.bytesLoaded} ms=${info.loadDurationMs}")
                    }
                    override fun onTracksChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, tracks: androidx.media3.common.Tracks) {
                        tracks.groups.forEach { group -> (0 until group.length).forEach { index ->
                            val format = group.getTrackFormat(index)
                            android.util.Log.i("PlaybackProbe", "track ${format.sampleMimeType} ${format.codecs} supported=${group.isTrackSupported(index)} selected=${group.isTrackSelected(index)}")
                        } }
                    }
                    override fun onPlayerError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, error: androidx.media3.common.PlaybackException) {
                        android.util.Log.i("PlaybackProbe", "error=${error.errorCodeName}")
                    }
                })
            }
            waitUntil(scenario) { activity ->
                val player = findPlayer(activity.findViewById(android.R.id.content))
                player?.isPlaying == true && (player.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 5
            }
            android.util.Log.i("PlaybackProbe", "$channelId startupMs=${android.os.SystemClock.elapsedRealtime() - selectedAt}")
            var start = 0L
            scenario.onActivity { start = streamPosition(findPlayer(it.findViewById(android.R.id.content))!!) }
            waitUntil(scenario) { streamPosition(findPlayer(it.findViewById(android.R.id.content))!!) > start + 3000 }
            // Refresh must complete alongside decoding within a normal TV heap.
            waitUntil(scenario) {
                cache.exists() && cache.length() in 1..16_000_000 && cache.lastModified() > previousCacheTime
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "verified-playback.png")
            file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            // Reveal controls if already auto-hidden, then exercise both Back layers.
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP)
            androidx.test.espresso.Espresso.pressBack()
            androidx.test.espresso.Espresso.pressBack()
            waitUntil(scenario) { it.findViewById<RecyclerView>(R.id.channel_grid).isShown }
            scenario.onActivity {
                assertEquals(query, it.findViewById<android.widget.EditText>(R.id.search_input).text.toString())
                assertNull(findPlayer(it.findViewById(android.R.id.content)))
            }
        }
    }
}
