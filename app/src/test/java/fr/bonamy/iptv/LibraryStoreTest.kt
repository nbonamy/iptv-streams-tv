package fr.bonamy.iptv

import android.content.Context
import fr.bonamy.iptv.core.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LibraryStoreTest {
    private lateinit var context: Context
    private val playlist = "#EXTM3U\n#EXTINF:-1 tvg-id=\"France2.fr@HD\",France 2\nhttps://example.com/fr.m3u8"
    private val metadata = """[{"id":"France2.fr","name":"France 2","country":"FR","categories":["general"]}]"""
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "library.json").delete()
        context.getSharedPreferences("viewer", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun `refresh writes a complete cache and bad refresh preserves it`() {
        val first = LibraryStore(context) { url -> if (url == Source.PLAYLIST) playlist else metadata }
        first.refresh(Library(playlist, metadata))
        val bad = LibraryStore(context) { "<html>unavailable</html>" }
        assertThrows(IllegalArgumentException::class.java) { bad.refresh(Library(playlist, metadata)) }
        assertEquals("France2.fr", bad.initial().second.single().id)
        val offline = LibraryStore(context) { throw java.io.IOException("offline") }
        assertThrows(java.io.IOException::class.java) { offline.refresh(Library(playlist, metadata)) }
        assertEquals("France2.fr", offline.initial().second.single().id)
    }
    @Test fun `cache stores only compact metadata rather than unused provider payload`() {
        val full = metadata.replace("\"categories\"", "\"unused\":\"${"x".repeat(1000000)}\",\"categories\"")
        LibraryStore(context) { if (it == Source.PLAYLIST) playlist else full }.refresh(Library(playlist, metadata))
        assertTrue(File(context.filesDir, "library.json").length() < 1000)
        assertEquals("FR", LibraryStore(context).initial().second.single().country)
    }
    @Test fun `bundled source loads offline and corrupt cache falls back to it`() {
        File(context.filesDir, "library.json").writeText("not json")
        val channels = LibraryStore(context).initial().second
        assertTrue(channels.size > 10000)
        assertTrue(Catalog.browse(channels, Section.FRANCE).size > 100)
        assertTrue(Catalog.browse(channels, Section.USA).size > 1000)
        assertTrue(Catalog.browse(channels, Section.SPORTS).size > 300)
    }
    @Test fun `favorites retain order through storage reload missing channel and URL rotation`() {
        val a = Channel("a", "A", "FR", emptyList(), listOf(Stream("https://example.com/a", "A")))
        val b = a.copy(id = "b", name = "B")
        val prefs = ViewerPreferences(context)
        prefs.toggle(a); prefs.toggle(b); prefs.move("b", -1)
        val restored = ViewerPreferences(context)
        assertEquals(listOf("b", "a"), restored.favorites())
        assertEquals(0, restored.apply(listOf(a)).first { it.id == "b" }.streams.size)
        assertEquals(listOf("b", "a"), Catalog.browse(restored.apply(listOf(a.copy(streams = listOf(Stream("https://example.com/new", "New"))))), Section.FAVORITES, restored.favorites()).map { it.id })
        restored.toggle(b); assertEquals(listOf("a"), restored.favorites())
    }
    @Test fun `explicit country and sports corrections persist and can return to source defaults`() {
        val channel = Channel("x", "X", null, listOf("news"), emptyList())
        ViewerPreferences(context).apply { country("x", "FR"); sports("x", true) }
        val prefs = ViewerPreferences(context)
        val corrected = prefs.apply(listOf(channel)).single()
        assertEquals("FR", corrected.country); assertTrue("sports" in corrected.categories)
        prefs.country("x", null); prefs.sports("x", null)
        assertNull(prefs.apply(listOf(channel)).single().country)
        assertFalse("sports" in prefs.apply(listOf(channel)).single().categories)
    }
}
