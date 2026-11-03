package fr.bonamy.iptv

import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.bonamy.iptv.core.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamPreferenceTest {
    @Test fun hdLeadsSourceMenuAndAnExplicitSdChoiceIsRetained() {
        val cached = Channel("fixture", "Quality fixture", "US", emptyList(), listOf(
            Stream("http://127.0.0.1:9/sd.m3u8", "Fixture SD"),
            Stream("http://127.0.0.1:9/hd.m3u8", "Fixture HD")))
        val channel = CatalogIndex(listOf(cached)).byId.getValue("fixture")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var screen: PlayerScreen? = null
            try {
                scenario.onActivity { activity ->
                    screen = PlayerScreen(activity, channel, ViewerPreferences(activity), 0)
                    activity.findViewById<FrameLayout>(android.R.id.content).addView(screen)
                    assertEquals("Fixture HD", screen!!.channel.streams[screen!!.streamIndex].label)
                }
                onView(withText("Source")).perform(click())
                onView(withText("1. Fixture HD")).check(matches(isDisplayed()))
                onView(withText("2. Fixture SD")).perform(click())
                scenario.onActivity { activity ->
                    assertEquals("Fixture SD", screen!!.channel.streams[screen!!.streamIndex].label)
                    val selected = screen!!.streamIndex
                    screen!!.release()
                    (screen!!.parent as FrameLayout).removeView(screen)
                    screen = PlayerScreen(activity, channel, ViewerPreferences(activity), selected)
                    activity.findViewById<FrameLayout>(android.R.id.content).addView(screen)
                    assertEquals("Fixture SD", screen!!.channel.streams[screen!!.streamIndex].label)
                }
            } finally {
                scenario.onActivity { screen?.release() }
            }
        }
    }
}
