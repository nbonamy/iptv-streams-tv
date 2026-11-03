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
    private val xml = """<tv><channel id="TF1.fr"><display-name>TF1</display-name></channel><programme channel="TF1.fr" start="20261103180000 +0100" stop="20261103190000 +0100"><title>News</title></programme></tv>"""
    @Before fun clear() { cache.delete() }
    @Test fun validCacheSurvivesFailedRefreshAndFreshCacheAvoidsNetwork() {
        GuideStore(context) { xml }.load()
        assertFalse(GuideStore(context) { error("No network expected") }.load().saved)
        assertTrue(GuideStore(context) { "invalid XML" }.load(force = true).saved)
        assertEquals(xml, cache.readText())
        assertTrue(GuideStore(context) { throw java.io.IOException() }.load(force = true).saved)
    }
    @Test fun missingOrCorruptCacheAndOfflineFailsWithoutInventingPrograms() {
        assertThrows(java.io.IOException::class.java) { GuideStore(context) { throw java.io.IOException() }.load() }
        cache.writeText("bad")
        assertEquals("News", GuideStore(context) { xml }.load().guide.programs.single().title)
    }
}
