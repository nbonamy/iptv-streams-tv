package fr.bonamy.iptv

import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderMenuTest {
    @Test fun headerOpensSharedMenuWithRemoteOk() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.refresh_button)).check(doesNotExist())
            scenario.onActivity { it.findViewById<View>(R.id.browser_menu).requestFocusFromTouch() }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            onView(withId(R.id.dialog_panel)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("Refresh")).inRoot(isDialog()).check(matches(isDisplayed()))
            pressBack()
            // Reopening and selecting the real custom action exercises the callback.
            onView(withId(R.id.browser_menu)).perform(click())
            val cache = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "library.json")
            val modified = cache.lastModified()
            onView(withText("Refresh")).inRoot(isDialog()).perform(click())
            val deadline = android.os.SystemClock.elapsedRealtime() + 60000
            var refreshed = false
            while (!refreshed && android.os.SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity {
                    refreshed = cache.lastModified() > modified && it.findViewById<android.widget.TextView>(R.id.browser_status).text.isEmpty()
                }
                if (!refreshed) android.os.SystemClock.sleep(100)
            }
            org.junit.Assert.assertTrue("Refresh must replace the saved catalog and finish", refreshed)
        }
    }
}
