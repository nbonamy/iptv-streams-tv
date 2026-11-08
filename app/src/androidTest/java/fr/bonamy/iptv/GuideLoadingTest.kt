package fr.bonamy.iptv

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.bonamy.iptv.core.Channel
import fr.bonamy.iptv.core.GuideChannel
import fr.bonamy.iptv.core.GuideProgram
import fr.bonamy.iptv.core.TvGuide
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GuideLoadingTest {
    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), predicate)?.let { return it }
        return null
    }
    @Test fun loadingAndRefreshKeepTheirProperContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var screen: GuideScreen
            scenario.onActivity { activity ->
                screen = GuideScreen(activity, {}, {}, overlayMode = true) {}
                (activity.window.decorView as ViewGroup).addView(screen, FrameLayout.LayoutParams(-1, -1))
                screen.loading()
                assertTrue(find(screen) { it is ProgressBar && it.isShown } != null)
            }
            instrumentation.waitForIdleSync()
            File(instrumentation.targetContext.getExternalFilesDir(null), "guide-loading.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            scenario.onActivity {
                val now = System.currentTimeMillis()
                val channel = Channel("TF1.fr", "TF1", "FR", emptyList(), emptyList())
                screen.show(SavedGuide(TvGuide(listOf(GuideChannel(channel.id, channel.name)),
                    listOf(GuideProgram(channel.id, "Evening news\nPlaying and laughing", "", now - 60_000, now + 3_600_000))), false), listOf(channel))
                assertNull(find(screen) { it is ProgressBar && it.isShown })
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val title = find(screen) { it is android.widget.TextView && it.text == "Evening news\nPlaying and laughing" } as android.widget.TextView
                val cell = title.parent as ViewGroup
                val topGap = cell.getChildAt(0).top
                val bottomGap = cell.height - title.bottom
                assertTrue("Program content should be vertically centered", kotlin.math.abs(topGap - bottomGap) <= title.context.dp(2))
                assertEquals(2, title.layout.lineCount)
                assertTrue("Second-line descenders are clipped", title.layout.getLineBaseline(1) + title.paint.fontMetricsInt.descent <= title.height - title.paddingBottom)
                assertTrue("Title extends outside the program cell", title.bottom <= (title.parent as View).height - (title.parent as View).paddingBottom)
            }
            android.os.SystemClock.sleep(300)
            File(instrumentation.targetContext.getExternalFilesDir(null), "guide-tall-cells.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            scenario.onActivity {
                screen.loading()
                assertNotNull(find(screen) { it.isShown && it.contentDescription?.contains("Playing and laughing") == true })
                screen.failed()
                assertNotNull(find(screen) { it is android.widget.TextView && it.isShown && it.text == "Retry" })
            }
        }
    }
}
