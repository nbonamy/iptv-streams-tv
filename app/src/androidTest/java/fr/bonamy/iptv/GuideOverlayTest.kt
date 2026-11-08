package fr.bonamy.iptv

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.bonamy.iptv.core.*
import org.junit.Assert.*
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Run separately: the class temporarily supplies an offline catalog and original live fixture. */
@RunWith(AndroidJUnit4::class)
class GuideOverlayTest {
    companion object {
        private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
        private lateinit var origin: ShortLiveWindowTest.Origin
        private var libraryBackup: ByteArray? = null
        private var guideBackup: ByteArray? = null
        private var preferences = emptyMap<String, Any?>()
        @JvmStatic @BeforeClass fun prepare() {
            val library = File(context.filesDir, "library.json")
            val guide = File(context.filesDir, "france-guide.xml")
            libraryBackup = library.takeIf { it.exists() }?.readBytes()
            guideBackup = guide.takeIf { it.exists() }?.readBytes()
            val prefs = context.getSharedPreferences("viewer", android.content.Context.MODE_PRIVATE)
            preferences = prefs.all.toMap(); prefs.edit().clear().commit()
            origin = ShortLiveWindowTest.Origin()
            val channels = listOf("TF1.fr" to "FR", "France2.fr" to "FR", "fixture.us" to "US").map { (id, country) ->
                Channel(id, if (id == "TF1.fr") "TF1" else if (id == "France2.fr") "France 2" else "US fixture", country,
                    listOf("general"), listOf(Stream(origin.url, "Original synthetic live stream")))
            }
            val gson = com.google.gson.Gson()
            library.writeText(org.json.JSONObject().put("version", 3).put("channels", org.json.JSONArray(gson.toJson(channels)))
                .put("playlist", "#EXTM3U").put("metadata", "[]").toString())
            val time = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC)
            val now = System.currentTimeMillis()
            val start = time.format(Instant.ofEpochMilli(now - 1800000))
            val end = time.format(Instant.ofEpochMilli(now + 7200000))
            guide.writeText("""<tv><channel id="TF1.fr"><display-name>TF1</display-name></channel><channel id="France2.fr"><display-name>France 2</display-name></channel>
                <programme channel="TF1.fr" start="$start" stop="$end"><title>Fixture TF1 news</title><desc>Original test schedule</desc></programme>
                <programme channel="France2.fr" start="$start" stop="$end"><title>Fixture France 2 news</title><desc>Original test schedule</desc></programme></tv>""")
        }
        @JvmStatic @AfterClass fun restore() {
            origin.close()
            for ((name, backup) in listOf("library.json" to libraryBackup, "france-guide.xml" to guideBackup)) {
                val file = File(context.filesDir, name)
                if (backup == null) file.delete() else file.writeBytes(backup)
            }
            val edit = context.getSharedPreferences("viewer", android.content.Context.MODE_PRIVATE).edit().clear()
            preferences.forEach { (key, value) -> when (value) { is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value) } }
            edit.commit()
        }
    }
    @Before fun resetLiveWindow() { origin.reset() }
    private fun player(view: View): ExoPlayer? {
        if (view is PlayerView) return view.player as? ExoPlayer
        if (view is ViewGroup) repeat(view.childCount) { index -> player(view.getChildAt(index))?.let { return it } }
        return null
    }
    private fun screen(view: View): PlayerScreen? {
        if (view is PlayerScreen) return view
        if (view is ViewGroup) repeat(view.childCount) { index -> screen(view.getChildAt(index))?.let { return it } }
        return null
    }
    private fun overlay(activity: MainActivity) = activity.window.decorView.findViewWithTag<GuideScreen>("tv-guide-overlay")
    private fun await(scenario: ActivityScenario<MainActivity>, check: (MainActivity) -> Boolean) {
        repeat(200) {
            var ready = false
            scenario.onActivity { ready = check(it) }
            if (ready) return
            android.os.SystemClock.sleep(100)
        }
        error("Guide overlay did not settle")
    }
    private fun key(code: Int) = InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(150)
        File(context.getExternalFilesDir(null), name).outputStream().use { out ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
    private fun openCountry(scenario: ActivityScenario<MainActivity>, index: Int) {
        await(scenario) { it.findViewById<TextView>(R.id.browser_status).text.isNullOrBlank() &&
            it.findViewById<RecyclerView>(R.id.country_grid).findViewHolderForAdapterPosition(index) != null }
        scenario.onActivity { it.findViewById<RecyclerView>(R.id.country_grid).findViewHolderForAdapterPosition(index)!!.itemView.performClick() }
        await(scenario) { it.findViewById<RecyclerView>(R.id.channel_grid).findViewHolderForAdapterPosition(0) != null }
    }
    private fun start(scenario: ActivityScenario<MainActivity>, country: Int = 1) {
        openCountry(scenario, country)
        scenario.onActivity {
            val grid = it.findViewById<RecyclerView>(R.id.channel_grid)
            val offset = if ((grid.adapter as ChannelAdapter).guideAvailable) 1 else 0
            grid.findViewHolderForAdapterPosition(offset)!!.itemView.performClick()
        }
        await(scenario) { player(it.window.decorView)?.isPlaying == true &&
            (player(it.window.decorView)?.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 3 }
    }
    @Test fun jumpAndBackKeepTheSameVideoPlayingWithGuideFocus() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openCountry(scenario, 1)
            capture("guide-card.png")
            scenario.onActivity {
                val holder = it.findViewById<RecyclerView>(R.id.channel_grid).findViewHolderForAdapterPosition(0) as ChannelAdapter.Holder
                assertEquals(android.graphics.Color.WHITE, (holder.logo.background as ColorDrawable).color)
            }
            scenario.onActivity { it.findViewById<RecyclerView>(R.id.channel_grid).findViewHolderForAdapterPosition(0)!!.itemView.performClick() }
            await(scenario) { it.findViewById<View>(R.id.tv_guide)?.isShown == true && it.currentFocus?.contentDescription?.toString()?.contains("Fixture TF1") == true }
            capture("guide-page.png")
            var guideStart = 0L
            scenario.onActivity { guideStart = it.findViewById<GuideScreen>(R.id.tv_guide).saveState().getLong("start") }
            key(KeyEvent.KEYCODE_MEDIA_REWIND)
            scenario.onActivity { assertEquals(guideStart - 10_800_000, it.findViewById<GuideScreen>(R.id.tv_guide).saveState().getLong("start")) }
            key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            scenario.onActivity { assertEquals(guideStart, it.findViewById<GuideScreen>(R.id.tv_guide).saveState().getLong("start")) }
            key(KeyEvent.KEYCODE_BACK)
            scenario.onActivity { it.findViewById<RecyclerView>(R.id.channel_grid).findViewHolderForAdapterPosition(1)!!.itemView.performClick() }
            await(scenario) { player(it.window.decorView)?.isPlaying == true }
            var engine: ExoPlayer? = null; var frames = 0
            scenario.onActivity { engine = player(it.window.decorView); frames = engine!!.videoDecoderCounters!!.renderedOutputBufferCount }
            key(KeyEvent.KEYCODE_LAST_CHANNEL)
            await(scenario) { overlay(it)?.isShown == true && it.currentFocus?.contentDescription?.toString()?.contains("Fixture TF1") == true }
            capture("guide-overlay.png")
            scenario.onActivity { guideStart = overlay(it)!!.saveState().getLong("start") }
            key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            scenario.onActivity { assertEquals(guideStart + 10_800_000, overlay(it)!!.saveState().getLong("start")); assertSame(engine, player(it.window.decorView)); assertTrue(engine!!.isPlaying) }
            key(KeyEvent.KEYCODE_MEDIA_REWIND)
            scenario.onActivity { assertEquals(guideStart, overlay(it)!!.saveState().getLong("start")) }
            await(scenario) { it.currentFocus?.contentDescription?.toString()?.contains("Fixture TF1") == true }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            await(scenario) { it.currentFocus?.contentDescription?.toString()?.contains("Fixture France 2") == true }
            await(scenario) { player(it.window.decorView) === engine && engine!!.isPlaying && engine!!.videoDecoderCounters!!.renderedOutputBufferCount > frames + 25 }
            key(KeyEvent.KEYCODE_BACK)
            scenario.onActivity { assertNull(overlay(it)); assertSame(engine, player(it.window.decorView)); assertTrue(engine!!.isPlaying) }
            key(KeyEvent.KEYCODE_LAST_CHANNEL)
            await(scenario) { overlay(it)?.isShown == true }
            key(KeyEvent.KEYCODE_LAST_CHANNEL)
            scenario.onActivity { assertNull(overlay(it)); assertSame(engine, player(it.window.decorView)) }
        }
    }
    @Test fun selectionReturnsToFullscreenAndOverlaySurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            start(scenario)
            key(KeyEvent.KEYCODE_LAST_CHANNEL)
            await(scenario) { overlay(it)?.isShown == true && it.currentFocus?.contentDescription?.toString()?.contains("Fixture TF1") == true }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            await(scenario) { it.currentFocus?.contentDescription?.toString()?.contains("Fixture France 2") == true }
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            await(scenario) { overlay(it) == null && screen(it.window.decorView)?.channel?.id == "France2.fr" && player(it.window.decorView)?.isPlaying == true }
            key(KeyEvent.KEYCODE_LAST_CHANNEL)
            await(scenario) { overlay(it)?.isShown == true && it.currentFocus?.contentDescription?.toString()?.contains("Fixture France 2") == true }
            scenario.recreate()
            await(scenario) { overlay(it)?.isShown == true && screen(it.window.decorView)?.channel?.id == "France2.fr" &&
                it.currentFocus?.contentDescription?.toString()?.contains("Fixture France 2") == true && player(it.window.decorView)?.isPlaying == true }
            key(KeyEvent.KEYCODE_BACK)
            scenario.onActivity { assertNull(overlay(it)); assertNotNull(screen(it.window.decorView)) }
        }
    }
    @Test fun otherCountriesDoNotOpenTheFranceGuide() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            start(scenario, 2)
            key(KeyEvent.KEYCODE_LAST_CHANNEL)
            scenario.onActivity { assertNull(overlay(it)); assertEquals("fixture.us", screen(it.window.decorView)?.channel?.id); assertTrue(player(it.window.decorView)!!.isPlaying) }
        }
    }
}
