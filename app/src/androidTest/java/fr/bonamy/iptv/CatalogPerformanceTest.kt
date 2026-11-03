package fr.bonamy.iptv

import android.os.SystemClock
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.bonamy.iptv.core.Catalog
import fr.bonamy.iptv.core.CountryChannelOrder
import fr.bonamy.iptv.core.Section
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class CatalogPerformanceTest {
    @Test fun firstRunSeedsALocalCatalogWithoutDownloading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = java.io.File(context.filesDir, "library.json")
        val backup = java.io.File(context.filesDir, "library.performance-backup")
        val existed = cache.exists()
        if (existed) check(cache.renameTo(backup))
        try {
            val began = SystemClock.elapsedRealtime()
            val store = LibraryStore(context) { error("Bundled local source must not download") }
            val loaded = store.initial()
            val seedMs = SystemClock.elapsedRealtime() - began
            android.util.Log.i("CatalogPerformance", "firstRunSeedMs=$seedMs channels=${loaded.channels.size}")
            val persistence = SystemClock.elapsedRealtime()
            store.persistIfNeeded()
            val persistMs = SystemClock.elapsedRealtime() - persistence
            android.util.Log.i("CatalogPerformance", "seedPersistenceMs=$persistMs")
            assertTrue(cache.isFile)
            assertTrue("First local catalog took $seedMs ms", seedMs < 1500)
            assertTrue("Initial catalog was not persisted promptly: $persistMs ms", persistMs < 10000)
        } finally {
            cache.delete()
            if (existed) check(backup.renameTo(cache))
        }
    }
    @Test fun localLoadAndCountryNavigationStayResponsive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LibraryStore(context) { error("Local startup must not download") }
        val started = SystemClock.elapsedRealtime()
        val loaded = store.initial()
        val localMs = SystemClock.elapsedRealtime() - started
        val order = CountryChannelOrder.parse(context.assets.open("channel-order.json").bufferedReader().use { it.readText() })
        val preferences = ViewerPreferences(context)
        val began = SystemClock.elapsedRealtime()
        val effective = loaded.channels
        preferences.favoriteChannels(loaded.channels.associateBy { it.id })
        val prefsMs = SystemClock.elapsedRealtime() - began
        val filtered = SystemClock.elapsedRealtime()
        val france = Catalog.browse(effective, Section.FRANCE, order = order)
        val filterMs = SystemClock.elapsedRealtime() - filtered
        android.util.Log.i("CatalogPerformance", "localMs=$localMs preferencesMs=$prefsMs filterMs=$filterMs channels=${loaded.channels.size} france=${france.size}")

        val launchAt = SystemClock.elapsedRealtime()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val readyBy = SystemClock.elapsedRealtime() + 20000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < readyBy) {
                scenario.onActivity { activity ->
                    val countries = activity.findViewById<RecyclerView>(R.id.country_grid)
                    ready = countries.isShown && countries.findViewHolderForAdapterPosition(1) != null &&
                        activity.findViewById<android.widget.TextView>(R.id.browser_status).text.toString() != "Loading channels…"
                }
                if (!ready) SystemClock.sleep(20)
            }
            assertTrue("Local library never became ready", ready)
            val readyMs = SystemClock.elapsedRealtime() - launchAt
            android.util.Log.i("CatalogPerformance", "activityReadyMs=$readyMs")
            repeat(3) {
                val drawn = AtomicLong()
                val clickAt = SystemClock.elapsedRealtime()
                scenario.onActivity { activity ->
                    val grid = activity.findViewById<RecyclerView>(R.id.channel_grid)
                    grid.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            if (grid.isShown && grid.childCount > 0 && (grid.adapter as ChannelAdapter).channels.firstOrNull()?.id == "TF1.fr") {
                                drawn.set(SystemClock.elapsedRealtime()); grid.viewTreeObserver.removeOnPreDrawListener(this)
                            }
                            return true
                        }
                    })
                    activity.findViewById<RecyclerView>(R.id.country_grid).findViewHolderForAdapterPosition(1)!!.itemView.performClick()
                }
                val deadline = clickAt + 10000
                while (drawn.get() == 0L && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
                val navigationMs = drawn.get() - clickAt
                android.util.Log.i("CatalogPerformance", "franceNavigationMs=$navigationMs")
                assertTrue("France should draw within 250 ms, took $navigationMs ms", navigationMs in 1..250)
                scenario.onActivity { activity ->
                    assertEquals("TF1.fr", (activity.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels.first().id)
                    activity.onBackPressedDispatcher.onBackPressed()
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            assertTrue("Local app startup took $readyMs ms", readyMs < 1500)
        }
        assertTrue("Local catalog took $localMs ms to load", localMs < 1500)
        assertTrue("Preferences took $prefsMs ms", prefsMs < 50)
        assertTrue("Country filtering took $filterMs ms", filterMs < 100)
    }
}
