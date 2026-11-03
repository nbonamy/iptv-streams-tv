package fr.bonamy.iptv

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowsingTest {
    private lateinit var saved: Map<String, *>
    private val prefs get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("viewer", android.content.Context.MODE_PRIVATE)
    @Before fun isolatePreferences() { saved = prefs.all.toMap(); prefs.edit().clear().commit() }
    @After fun restorePreferences() {
        val edit = prefs.edit().clear()
        saved.forEach { (key, value) -> when (value) { is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value) } }
        edit.commit()
    }
    private fun awaitChannels(scenario: ActivityScenario<MainActivity>) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 30000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var loaded = false
            scenario.onActivity { loaded = it.findViewById<android.widget.TextView>(R.id.browser_status).text.toString() != "Loading channels…" }
            if (loaded) return
            android.os.SystemClock.sleep(150)
        }
        throw AssertionError("Catalog did not load")
    }
    private fun awaitGrid(scenario: ActivityScenario<MainActivity>, predicate: (List<fr.bonamy.iptv.core.Channel>) -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var settled = false
            scenario.onActivity {
                val grid = it.findViewById<RecyclerView>(R.id.channel_grid)
                val channels = (grid.adapter as ChannelAdapter).channels
                settled = grid.isShown && channels.isNotEmpty() && predicate(channels)
            }
            if (settled) return
            android.os.SystemClock.sleep(100)
        }
        throw AssertionError("Expected filtered channels did not appear")
    }
    private fun home(scenario: ActivityScenario<MainActivity>) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity {
                val grid = it.findViewById<RecyclerView>(R.id.country_grid)
                ready = grid.isShown && grid.hasFocus() && (grid.adapter as CountryAdapter).countries.take(5).map { tile -> tile.name } ==
                    listOf("Favorites", "France", "USA", "UK", "Canada")
            }
            if (ready) return
            android.os.SystemClock.sleep(100)
        }
        throw AssertionError("Home destination tiles did not appear")
    }
    private fun tile(position: Int) {
        onView(withId(R.id.country_grid)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(position, click()))
    }
    @Test fun favoritesSearchCountriesAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitChannels(scenario); home(scenario)
            onView(withId(R.id.browser_hero)).check(matches(isDisplayed()))
            onView(allOf(withText("France"), isAssignableFrom(android.widget.Button::class.java)))
                .check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            tile(1)
            awaitGrid(scenario) { list -> list.all { it.country == "FR" } }
            onView(withText("Hold Select for channel options")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            onView(withId(R.id.browser_status)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.browser_hero)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            scenario.onActivity {
                val grid = it.findViewById<RecyclerView>(R.id.channel_grid)
                org.junit.Assert.assertEquals(6, (grid.layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanCount)
                val channels = (grid.adapter as ChannelAdapter).channels
                org.junit.Assert.assertEquals(listOf("TF1.fr", "France2.fr", "France3.fr"), channels.take(3).map { channel -> channel.id })
                org.junit.Assert.assertEquals("TV guide", (grid.findViewHolderForAdapterPosition(0) as ChannelAdapter.Holder).name.text.toString())
                val holder = grid.findViewHolderForAdapterPosition(1) as ChannelAdapter.Holder
                org.junit.Assert.assertTrue("Channel cards should be portrait", holder.card.height > holder.card.width)
                val logo = IntArray(2); val name = IntArray(2)
                holder.logo.getLocationOnScreen(logo); holder.name.getLocationOnScreen(name)
                org.junit.Assert.assertTrue("Name belongs below the logo", name[1] >= logo[1] + holder.logo.height)
            }
            onView(withId(R.id.channel_grid)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, longClick()))
            onView(withText("Country")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            onView(withText("Sports category")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            onView(withText("Add to favorites")).perform(click())
            androidx.test.espresso.Espresso.pressBack(); home(scenario)
            onView(withId(R.id.browser_hero)).check(matches(isDisplayed()))
            scenario.onActivity { org.junit.Assert.assertEquals("France", it.currentFocus?.contentDescription) }
            tile(0)
            awaitGrid(scenario) { it.size == 1 }
            scenario.recreate(); awaitChannels(scenario)
            awaitGrid(scenario) { it.size == 1 }
            onView(withId(R.id.search_button)).perform(click())
            onView(withId(R.id.search_input)).perform(replaceText("France 2"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
            awaitGrid(scenario) { list -> list.all { it.name.contains("France 2", ignoreCase = true) } }
            androidx.test.espresso.Espresso.pressBack()
            onView(withId(R.id.search_input)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            awaitGrid(scenario) { it.size == 1 }
            androidx.test.espresso.Espresso.pressBack(); home(scenario)
            var canada = 0
            scenario.onActivity { canada = (it.findViewById<RecyclerView>(R.id.country_grid).adapter as CountryAdapter).countries.indexOfFirst { tile -> tile.code == "CA" } }
            tile(canada)
            awaitGrid(scenario) { list -> list.all { it.country == "CA" } }
            androidx.test.espresso.Espresso.pressBack(); home(scenario)
            tile(2)
            awaitGrid(scenario) { list -> list.all { it.country == "US" } }
            scenario.onActivity {
                val ids = (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.map { channel -> channel.id }
                if ("ESPN.us" in ids) org.junit.Assert.assertEquals("ESPN.us", ids.first())
                if ("FX.us" in ids && "CNN.us" in ids) org.junit.Assert.assertTrue(ids.indexOf("FX.us") < ids.indexOf("CNN.us"))
            }
            androidx.test.espresso.Espresso.pressBack(); home(scenario)
            tile(3)
            awaitGrid(scenario) { list -> list.all { it.country == "GB" } }
            scenario.onActivity {
                val ids = (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.map { channel -> channel.id }
                val core = listOf("BBCOne.uk", "BBCTwo.uk", "ITV1.uk", "Channel4.uk", "Channel5.uk", "ITV2.uk").filter { id -> id in ids }
                org.junit.Assert.assertEquals(core, ids.take(core.size))
            }
            androidx.test.espresso.Espresso.pressBack(); home(scenario)
            tile(4)
            awaitGrid(scenario) { list -> list.all { it.country == "CA" } }
        }
    }
}
