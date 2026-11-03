package fr.bonamy.iptv

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchTest {
    private fun await(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 30000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            android.os.SystemClock.sleep(100)
        }
        throw AssertionError("Search state did not settle")
    }
    @Test fun emptyQueryHasNoResultsAndSearchHasNoArtwork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { it.findViewById<android.widget.TextView>(R.id.browser_status).text.toString() != "Loading channels…" }
            onView(withId(R.id.search_button)).perform(click())
            onView(withId(R.id.browser_hero)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            await(scenario) {
                val grid = it.findViewById<RecyclerView>(R.id.channel_grid)
                !grid.isShown && grid.adapter!!.itemCount == 0
            }
            onView(withId(R.id.search_input)).perform(replaceText("France 24"), closeSoftKeyboard())
            await(scenario) {
                val grid = it.findViewById<RecyclerView>(R.id.channel_grid)
                val items = (grid.adapter as ChannelAdapter).channels
                grid.isShown && items.size == 1 && items.single().id == "France24.fr"
            }
            onView(org.hamcrest.Matchers.allOf(withContentDescription("France"), isDisplayed())).check(matches(isDisplayed()))
            onView(withId(R.id.search_input)).perform(replaceText("   "), closeSoftKeyboard())
            await(scenario) {
                val grid = it.findViewById<RecyclerView>(R.id.channel_grid)
                !grid.isShown && grid.adapter!!.itemCount == 0
            }
            androidx.test.espresso.Espresso.pressBack()
            onView(withId(R.id.browser_hero)).check(matches(isDisplayed()))
        }
    }
}
