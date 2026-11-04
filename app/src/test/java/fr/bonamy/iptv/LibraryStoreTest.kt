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
    @Test fun `global refresh and offline cache keep source data while effective NBC uses country override`() {
        val source = "#EXTM3U\n#EXTINF:-1 tvg-id=\"NBC.us@HD\",NBC\nhttps://example.com/downloaded-nbc"
        val info = """[{"id":"NBC.us","name":"NBC","country":"US","categories":["general"]}]"""
        val fetched = mutableListOf<String>()
        LibraryStore(context) { url -> fetched.add(url); if (url == Source.PLAYLIST) source else info }.refresh()
        assertEquals(listOf(Source.PLAYLIST, Source.METADATA), fetched)
        val cached = LibraryStore(context) { error("Offline startup must not download") }.initial()
        assertEquals(source, cached.source.playlist)
        assertEquals("https://example.com/downloaded-nbc", cached.channels.single().streams.single().url)
        val order = CountryChannelOrder.parse(context.assets.open("channel-order.json").bufferedReader().use { it.readText() })
        val effective = CatalogIndex(cached.channels, order).byId.getValue("NBC.us")
        assertEquals("NBC Chicago WMAQ-TV (1080p)", effective.streams.first().label)
        assertEquals(2, effective.streams.size)
    }
    @Test fun `refresh writes a complete cache and bad refresh preserves it`() {
        val first = LibraryStore(context) { url -> if (url == Source.PLAYLIST) playlist else metadata }
        first.refresh(Library(playlist, metadata))
        val bad = LibraryStore(context) { "<html>unavailable</html>" }
        assertThrows(IllegalArgumentException::class.java) { bad.refresh(Library(playlist, metadata)) }
        assertEquals("France2.fr", bad.initial().channels.single().id)
        val offline = LibraryStore(context) { throw java.io.IOException("offline") }
        assertThrows(java.io.IOException::class.java) { offline.refresh(Library(playlist, metadata)) }
        assertEquals("France2.fr", offline.initial().channels.single().id)
    }
    @Test fun `cache stores only compact metadata rather than unused provider payload`() {
        val full = metadata.replace("\"categories\"", "\"unused\":\"${"x".repeat(1000000)}\",\"categories\"")
        LibraryStore(context) { if (it == Source.PLAYLIST) playlist else full }.refresh(Library(playlist, metadata))
        assertTrue(File(context.filesDir, "library.json").length() < 2000)
        assertEquals("FR", LibraryStore(context).initial().channels.single().country)
    }
    @Test fun `bundled source loads offline and corrupt cache falls back to it`() {
        File(context.filesDir, "library.json").writeText("not json")
        val channels = LibraryStore(context).initial().channels
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
        assertEquals(0, restored.favoriteChannels(mapOf(a.id to a)).first { it.id == "b" }.streams.size)
        assertEquals(listOf("b", "a"), restored.favoriteChannels(mapOf(a.id to a.copy(streams = listOf(Stream("https://example.com/new", "New"))))).map { it.id })
        restored.toggle(b); assertEquals(listOf("a"), restored.favorites())
    }
    @Test fun `saved parsed catalog is reused without reparsing raw M3U`() {
        val store = LibraryStore(context) { if (it == Source.PLAYLIST) playlist else metadata }
        val first = store.refresh(Library(playlist, metadata))
        assertSame(store.initial(), store.initial())
        assertEquals(first.channels, store.initial().channels)
        val cache = File(context.filesDir, "library.json")
        // The parsed cache remains usable even if its source text cannot be parsed.
        val json = org.json.JSONObject(cache.readText()).put("playlist", "cached raw source is not reparsed")
        cache.writeText(json.toString())
        val reopened = LibraryStore(context) { error("Offline startup attempted a download") }.initial()
        assertEquals(first.channels, reopened.channels)
    }
    @Test fun `older text only caches upgrade once and favorites ignore legacy metadata overrides`() {
        File(context.filesDir, "library.json").writeText(org.json.JSONObject().put("playlist", playlist).put("metadata", metadata).toString())
        val channels = LibraryStore(context).initial().channels
        assertEquals("FR", channels.single().country)
        assertEquals(3, org.json.JSONObject(File(context.filesDir, "library.json").readText()).getInt("version"))
        context.getSharedPreferences("viewer", Context.MODE_PRIVATE).edit().putString("country:France2.fr", "US").putBoolean("sports:France2.fr", true).commit()
        val prefs = ViewerPreferences(context)
        prefs.toggle(channels.single())
        assertSame(channels.single(), prefs.favoriteChannels(channels.associateBy { it.id }).single())
    }
    @Test fun `invalid materialized channels rebuild from the saved source`() {
        val json = org.json.JSONObject().put("version", 2).put("playlist", playlist).put("metadata", metadata)
            .put("channels", org.json.JSONArray("[{}]"))
        File(context.filesDir, "library.json").writeText(json.toString())
        val loaded = LibraryStore(context) { error("Local recovery must not download") }.initial()
        assertEquals("France2.fr", loaded.channels.single().id)
    }
    @Test fun `a blocked refresh keeps the existing in memory catalog immediately readable`() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        var block = false
        val store = LibraryStore(context) {
            if (block) { entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            if (it == Source.PLAYLIST) playlist else metadata
        }
        store.refresh(Library(playlist, metadata))
        val original = store.initial()
        block = true
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit { store.refresh(original.source) }
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertSame(original, store.initial())
            release.countDown(); pending.get(3, java.util.concurrent.TimeUnit.SECONDS)
            assertNotSame(original, store.initial())
        } finally { release.countDown(); executor.shutdownNow() }
    }
    @Test fun `bundled catalog is available before persistence and an explicit refresh supersedes the pending seed`() {
        val store = LibraryStore(context) { if (it == Source.PLAYLIST) playlist else metadata }
        val bundled = store.initial()
        assertTrue(bundled.channels.size > 10000)
        assertFalse(File(context.filesDir, "library.json").exists())
        val refreshed = store.refresh()
        store.persistIfNeeded()
        assertEquals(refreshed.channels, LibraryStore(context).initial().channels)
        assertEquals(listOf("France2.fr"), refreshed.channels.map { it.id })
    }
    @Test fun `the initial bundled source is retained in a complete durable snapshot`() {
        val store = LibraryStore(context) { error("Local source must not download") }
        val initial = store.initial()
        store.persistIfNeeded()
        val reopened = LibraryStore(context).initial()
        assertEquals(initial.channels, reopened.channels)
        assertEquals(initial.source.playlist, reopened.source.playlist)
        assertEquals(initial.source.metadataJson, reopened.source.metadataJson)
    }
}
