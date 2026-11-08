package fr.bonamy.iptv

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import fr.bonamy.iptv.core.Channel
import fr.bonamy.iptv.core.GuideChannel
import fr.bonamy.iptv.core.GuideProgram
import fr.bonamy.iptv.core.TvGuide
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class GuideScreenTest {
    @Test fun finishedProgramsCannotBeSelectedButCurrentAndFutureCan() {
        val now = System.currentTimeMillis()
        val channel = Channel("TF1.fr", "TF1", "FR", emptyList(), emptyList())
        val programs = listOf(
            GuideProgram(channel.id, "Finished", "", now - 300_000, now - 60_000),
            GuideProgram(channel.id, "Current", "", now - 60_000, now + 60_000),
            GuideProgram(channel.id, "Next", "", now + 60_000, now + 1_200_000),
        )
        val played = mutableListOf<Channel>()
        val screen = GuideScreen(RuntimeEnvironment.getApplication(), { played.add(it) }, {}) {}
        screen.restoreState(Bundle().apply {
            putLong("start", now - now % 1_800_000 - 1_800_000)
            putBoolean("followsNow", false)
        })
        screen.show(SavedGuide(TvGuide(listOf(GuideChannel(channel.id, channel.name)), programs), false), listOf(channel))
        fun find(view: View, title: String): View? {
            if (view.contentDescription?.contains(title) == true) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), title)?.let { return it }
            return null
        }
        val finished = find(screen, "Finished")!!
        assertFalse(finished.isEnabled)
        assertFalse(finished.isFocusable)
        finished.performClick()
        assertTrue(played.isEmpty())
        find(screen, "Current")!!.performClick()
        find(screen, "Next")!!.performClick()
        assertEquals(listOf(channel, channel), played)
    }
}
