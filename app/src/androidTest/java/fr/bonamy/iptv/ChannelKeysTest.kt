package fr.bonamy.iptv

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelKeysTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val prefs get() = instrumentation.targetContext.getSharedPreferences("viewer", android.content.Context.MODE_PRIVATE)
    private var saved = emptyMap<String, Any?>()
    @Before fun savePreferences() { saved = prefs.all.toMap(); prefs.edit().clear().commit() }
    @After fun restorePreferences() {
        val edit = prefs.edit().clear()
        saved.forEach { (key, value) -> when (value) { is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value) } }
        edit.commit()
    }
    private fun players(view: View): List<PlayerScreen> = when (view) {
        is PlayerScreen -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { players(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun await(scenario: ActivityScenario<MainActivity>, predicate: (MainActivity) -> Boolean) {
        repeat(150) {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            android.os.SystemClock.sleep(100)
        }
        error("Channel navigation did not settle")
    }
    private fun country(scenario: ActivityScenario<MainActivity>, tile: Int): List<String> {
        await(scenario) { it.findViewById<android.widget.TextView>(R.id.browser_status).text.isNullOrBlank() &&
            it.findViewById<RecyclerView>(R.id.country_grid).findViewHolderForAdapterPosition(tile) != null }
        scenario.onActivity { it.findViewById<RecyclerView>(R.id.country_grid).findViewHolderForAdapterPosition(tile)!!.itemView.performClick() }
        await(scenario) { it.findViewById<RecyclerView>(R.id.channel_grid).isShown &&
            (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.isNotEmpty() }
        var ids = emptyList<String>()
        scenario.onActivity { ids = (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.map { channel -> channel.id } }
        return ids
    }
    private fun startFirst(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity {
            val grid = it.findViewById<RecyclerView>(R.id.channel_grid)
            val offset = if ((grid.adapter as ChannelAdapter).guideAvailable) 1 else 0
            grid.findViewHolderForAdapterPosition(offset)!!.itemView.performClick()
        }
    }
    private fun playing(scenario: ActivityScenario<MainActivity>, id: String) {
        await(scenario) { players(it.window.decorView).singleOrNull()?.channel?.id == id }
        scenario.onActivity { assertEquals("Only one player view may remain attached", 1, players(it.window.decorView).size) }
    }
    private fun key(code: Int) = instrumentation.sendKeyDownUpSync(code)
    private fun backToGrid(scenario: ActivityScenario<MainActivity>) {
        repeat(2) {
            var active = false
            scenario.onActivity { active = players(it.window.decorView).isNotEmpty() }
            if (active) key(KeyEvent.KEYCODE_BACK)
        }
        await(scenario) { it.findViewById<RecyclerView>(R.id.channel_grid).isShown }
    }
    @Test fun countryOrderWrapKeyRepeatAndBackFocus() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val ids = country(scenario, 2)
            startFirst(scenario); playing(scenario, ids[0])
            key(KeyEvent.KEYCODE_CHANNEL_UP); playing(scenario, ids[1])
            key(KeyEvent.KEYCODE_CHANNEL_DOWN); playing(scenario, ids[0])
            key(KeyEvent.KEYCODE_CHANNEL_DOWN); playing(scenario, ids.last())
            key(KeyEvent.KEYCODE_CHANNEL_UP); playing(scenario, ids[0])
            scenario.onActivity {
                val now = android.os.SystemClock.uptimeMillis()
                it.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CHANNEL_UP, 1))
                it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CHANNEL_UP))
            }
            playing(scenario, ids[0])
            key(KeyEvent.KEYCODE_CHANNEL_UP); playing(scenario, ids[1])
            backToGrid(scenario)
            await(scenario) { it.currentFocus?.contentDescription == ids[1].let { id ->
                (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.first { channel -> channel.id == id }.name } }
        }
    }
    @Test fun favoritesSkipUnavailableKeepManualOrderAndSurviveRecreation() {
        prefs.edit().putString("favorites", "[\"France3.fr\",\"missing\",\"TF1.fr\",\"France2.fr\"]").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(listOf("France3.fr", "missing", "TF1.fr", "France2.fr"), country(scenario, 0))
            startFirst(scenario); playing(scenario, "France3.fr")
            key(KeyEvent.KEYCODE_CHANNEL_UP); playing(scenario, "TF1.fr")
            key(KeyEvent.KEYCODE_CHANNEL_UP); playing(scenario, "France2.fr")
            key(KeyEvent.KEYCODE_CHANNEL_UP); playing(scenario, "France3.fr")
            key(KeyEvent.KEYCODE_CHANNEL_DOWN); playing(scenario, "France2.fr")
            scenario.recreate(); playing(scenario, "France2.fr")
            key(KeyEvent.KEYCODE_CHANNEL_UP); playing(scenario, "France3.fr")
            assertEquals("[\"France3.fr\",\"missing\",\"TF1.fr\",\"France2.fr\"]", prefs.getString("favorites", null))
        }
    }
    @Test fun searchResultsRemainTheChannelSequenceAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { it.findViewById<android.widget.TextView>(R.id.browser_status).text.isNullOrBlank() }
            onView(withId(R.id.search_button)).perform(click())
            onView(withId(R.id.search_input)).perform(replaceText("France"), closeSoftKeyboard())
            await(scenario) { it.findViewById<RecyclerView>(R.id.channel_grid).isShown &&
                (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.size > 1 }
            var ids = emptyList<String>()
            scenario.onActivity { ids = (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.map { channel -> channel.id } }
            startFirst(scenario); playing(scenario, ids[0])
            key(KeyEvent.KEYCODE_CHANNEL_UP); playing(scenario, ids[1])
            scenario.recreate(); playing(scenario, ids[1])
            key(KeyEvent.KEYCODE_CHANNEL_DOWN); playing(scenario, ids[0])
            backToGrid(scenario)
            await(scenario) { (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.map { channel -> channel.id } == ids }
        }
    }
}
