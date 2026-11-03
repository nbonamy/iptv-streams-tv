package fr.bonamy.iptv

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.bonamy.iptv.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit network acceptance; run separately from deterministic guide coverage. */
@RunWith(AndroidJUnit4::class)
class LiveGuideTest {
    @Test fun liveXmltvLoadsIntoNativeGrid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val result = GuideStore(context).load(force = true)
        assertFalse("Must fetch a fresh live feed", result.saved)
        assertTrue(result.guide.channels.size >= 20)
        assertTrue(result.guide.programs.size > 100)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun await(predicate: (MainActivity) -> Boolean) {
                repeat(300) {
                    var ready = false
                    scenario.onActivity { ready = predicate(it) }
                    if (ready) return
                    android.os.SystemClock.sleep(100)
                }
                error("Live guide did not settle")
            }
            await { it.findViewById<TextView>(R.id.browser_status).text.isBlank() && it.findViewById<RecyclerView>(R.id.country_grid).findViewHolderForAdapterPosition(1) != null }
            scenario.onActivity { it.findViewById<RecyclerView>(R.id.country_grid).findViewHolderForAdapterPosition(1)!!.itemView.performClick() }
            await { it.findViewById<RecyclerView>(R.id.channel_grid).findViewHolderForAdapterPosition(0) != null }
            scenario.onActivity { it.findViewById<RecyclerView>(R.id.channel_grid).findViewHolderForAdapterPosition(0)!!.itemView.performClick() }
            await { it.findViewById<View>(R.id.tv_guide)?.isShown == true && it.currentFocus?.contentDescription?.toString()?.startsWith("TF1,") == true }
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                java.io.File(context.filesDir, "live-guide.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
