package fr.bonamy.iptv

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class GuideTest {
    private fun player(view: View): PlayerScreen? {
        if (view is PlayerScreen) return view
        if (view is android.view.ViewGroup) repeat(view.childCount) { index -> player(view.getChildAt(index))?.let { return it } }
        return null
    }
    private fun await(scenario: ActivityScenario<MainActivity>, predicate: (MainActivity) -> Boolean) {
        repeat(200) {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            android.os.SystemClock.sleep(100)
        }
        var state = ""
        scenario.onActivity { state = "guide=${it.findViewById<View>(R.id.tv_guide)?.isShown}, focus=${it.currentFocus}, description=${it.currentFocus?.contentDescription}" }
        error("Guide UI did not settle: $state")
    }
    @Test fun programOpensChannelAndBackRestoresGuideAndTile() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(ctx.filesDir, "france-guide.xml")
        val old = if (file.exists()) file.readBytes() else null
        val format = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC)
        val start = System.currentTimeMillis() - 3600000
        file.writeText("""<tv><channel id="TF1.fr"><display-name>TF1</display-name></channel><channel id="France2.fr"><display-name>France 2</display-name></channel>
            <programme channel="TF1.fr" start="${format.format(Instant.ofEpochMilli(start))}" stop="${format.format(Instant.ofEpochMilli(start + 7200000))}"><title>Guide test TF1</title></programme>
            <programme channel="France2.fr" start="${format.format(Instant.ofEpochMilli(start))}" stop="${format.format(Instant.ofEpochMilli(start + 7200000))}"><title>Guide test France 2</title></programme></tv>""")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await(scenario) { it.findViewById<RecyclerView>(R.id.country_grid).findViewHolderForAdapterPosition(1) != null && it.findViewById<android.widget.TextView>(R.id.browser_status).text.isBlank() }
                scenario.onActivity { it.findViewById<RecyclerView>(R.id.country_grid).findViewHolderForAdapterPosition(1)!!.itemView.performClick() }
                await(scenario) { it.findViewById<RecyclerView>(R.id.channel_grid).findViewHolderForAdapterPosition(0) != null }
                scenario.onActivity { it.findViewById<RecyclerView>(R.id.channel_grid).findViewHolderForAdapterPosition(0)!!.itemView.performClick() }
                await(scenario) { it.currentFocus?.contentDescription?.toString()?.contains("Guide test TF1") == true }
                // Remote Down chooses the next channel, OK launches its live player.
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                await(scenario) { it.currentFocus?.contentDescription?.toString()?.contains("Guide test France 2") == true }
                scenario.recreate()
                await(scenario) { it.findViewById<View>(R.id.tv_guide)?.isShown == true && it.currentFocus?.contentDescription?.toString()?.contains("Guide test France 2") == true }
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                await(scenario) { player(it.window.decorView)?.isShown == true && it.findViewById<View>(R.id.tv_guide)?.isShown == false }
                scenario.onActivity { activity ->
                    assertEquals("France2.fr", player(activity.window.decorView)?.channel?.id)
                }
                // Controls have layered Back behavior; eventually return to the guide.
                repeat(3) {
                    var inPlayer = false
                    scenario.onActivity { inPlayer = it.findViewById<View>(R.id.tv_guide)?.isShown == false }
                    if (inPlayer) InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                }
                await(scenario) { it.findViewById<View>(R.id.tv_guide)?.isShown == true && it.currentFocus?.contentDescription?.toString()?.contains("Guide test") == true }
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await(scenario) { it.currentFocus?.contentDescription == "TV guide" }
            }
        } finally { if (old != null) file.writeBytes(old) else file.delete() }
    }
}
