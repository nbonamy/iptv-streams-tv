package fr.bonamy.iptv

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class GuideStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val cache get() = File(context.filesDir, "france-guide.xml")
    private fun schedule(start: Long, end: Long): String {
        val format = java.time.format.DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(java.time.ZoneOffset.UTC)
        return """<tv><channel id="TF1.fr"><display-name>TF1</display-name></channel><programme channel="TF1.fr" start="${format.format(java.time.Instant.ofEpochMilli(start))}" stop="${format.format(java.time.Instant.ofEpochMilli(end))}"><title>News</title></programme></tv>"""
    }
    private val xml = schedule(System.currentTimeMillis() - 60_000, System.currentTimeMillis() + 3_600_000)
    @Before fun clear() { cache.delete() }
    @Test fun validCacheSurvivesFailedRefreshAndFreshCacheAvoidsNetwork() {
        GuideStore(context) { xml }.load()
        assertFalse(GuideStore(context) { error("No network expected") }.load().saved)
        assertTrue(GuideStore(context) { "invalid XML" }.load(force = true).saved)
        assertEquals(xml, cache.readText())
        assertTrue(GuideStore(context) { throw java.io.IOException() }.load(force = true).saved)
    }
    @Test fun parsedGuideIsReusedUntilExpiredOrRefreshed() {
        var downloads = 0
        val store = GuideStore(context) { downloads++; xml }
        val first = store.load()
        assertSame(first.guide, store.load().guide)
        assertEquals(1, downloads)
        assertTrue(cache.setLastModified(System.currentTimeMillis() - 59 * 60_000L))
        val fresh = store.load()
        assertSame(fresh.guide, store.load().guide)
        assertEquals(1, downloads)
        assertTrue(cache.setLastModified(System.currentTimeMillis() - 61 * 60_000L))
        store.load()
        assertEquals(2, downloads)
        store.load(force = true)
        assertEquals(3, downloads)
    }
    @Test fun recentCacheWithoutCurrentProgramsIsRefreshed() {
        val now = System.currentTimeMillis()
        cache.writeText(schedule(now - 3_600_000, now - 60_000))
        var downloads = 0
        GuideStore(context) { downloads++; xml }.load()
        assertEquals(1, downloads)
        assertEquals(xml, cache.readText())
    }
    @Test fun missingOrCorruptCacheAndOfflineFailsWithoutInventingPrograms() {
        assertThrows(java.io.IOException::class.java) { GuideStore(context) { throw java.io.IOException() }.load() }
        cache.writeText("bad")
        assertEquals("News", GuideStore(context) { xml }.load().guide.programs.single().title)
    }
}
