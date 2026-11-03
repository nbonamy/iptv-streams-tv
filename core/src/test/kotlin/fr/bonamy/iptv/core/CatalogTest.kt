package fr.bonamy.iptv.core

import kotlin.test.*

class CatalogTest {
    @Test fun `logos survive variants and reject non network URLs`() {
        val channels = Catalog.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"a@HD\" tvg-logo=\"file:///private/logo.png\",A\nhttps://example.com/a\n#EXTINF:-1 tvg-id=\"a@SD\" tvg-logo=\"https://example.com/a.png\",A SD\nhttps://example.com/b", emptyMap())
        assertEquals("https://example.com/a.png", channels.single().logoUrl)
    }
    @Test fun `UK provider country code normalizes to the flag and browsing country`() {
        val info = Catalog.metadata("""[{"id":"BBCOne.uk","name":"BBC One","country":"UK","categories":["general"]}]""")
        val channels = Catalog.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"BBCOne.uk@HD\",BBC One\nhttps://example.com/uk.m3u8", info)
        assertEquals("GB", channels.single().country)
        assertEquals(1, Catalog.browse(channels, Section.ALL, country = "GB").size)
    }
    @Test fun `metadata compaction skips unused provider fields and rejects empty responses`() {
        val json = """[{"id":"France2.fr","name":"France 2","country":"FR","categories":["general"],"owners":[{"unused":"${"x".repeat(100000)}"}]}]"""
        val compact = Catalog.compactMetadata(json)
        assertTrue(compact.length < 200)
        assertEquals(Catalog.metadata(json), Catalog.metadata(compact))
        assertFailsWith<IllegalArgumentException> { Catalog.metadata("[]") }
    }
    private val metadata = listOf(
        Metadata("France2.fr", "France 2", "FR", listOf("general")),
        Metadata("Sport.us", "Sport", "US", listOf("sports")),
        Metadata("French.ca", "French Canada", "CA", listOf("general")),
    ).associateBy { it.id }

    @Test fun `joins variant ids, keeps alternatives, and never treats language as country`() {
        val channels = Catalog.parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="France2.fr@HD" group-title="General",France 2 HD
            https://example.com/fr.m3u8
            #EXTINF:-1 tvg-id="France2.fr@SD",France 2 SD
            https://example.com/fr-sd.m3u8
            #EXTINF:-1 tvg-id="French.ca",French channel
            https://example.com/ca.m3u8
            #EXTINF:-1,Unknown
            https://example.com/unknown
        """.trimIndent(), metadata)
        assertEquals(3, channels.size)
        assertEquals(2, channels.first { it.id == "France2.fr" }.streams.size)
        assertEquals(listOf("France2.fr"), Catalog.browse(channels, Section.FRANCE).map { it.id })
        assertEquals(3, Catalog.browse(channels, Section.ALL).size)
    }

    @Test fun `supports BOM CRLF comma in name directives and resets per entry headers`() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:-1 tvg-id=\"a\" http-user-agent=\"Agent\" group-title=\"Sports;News\",One, Two\r\n" +
            "#EXTVLCOPT:http-referrer=https://example.com/\r\nhttps://example.com/a.m3u8\r\n" +
            "#EXTINF:-1 tvg-id=\"b\",Other\r\nhttps://example.com/b.mp4\r\n"
        val channels = Catalog.parse(text, emptyMap())
        val first = channels.first { it.id == "a" }
        assertEquals("One, Two", first.name)
        assertEquals("Agent", first.streams.single().headers["User-Agent"])
        assertEquals("https://example.com/", first.streams.single().headers["Referer"])
        assertTrue(channels.first { it.id == "b" }.streams.single().headers.isEmpty())
        assertEquals(listOf("a"), Catalog.browse(channels, Section.SPORTS).map { it.id })
    }

    @Test fun `rejects HTML empty catalogs and unsafe stream schemes without losing valid channels`() {
        assertFailsWith<IllegalArgumentException> { Catalog.parse("<html>error</html>", metadata) }
        assertFailsWith<IllegalArgumentException> { Catalog.parse("#EXTM3U", metadata) }
        val channels = Catalog.parse("#EXTM3U\n#EXTINF:-1,Bad\nfile:///etc/passwd\n#EXTINF:-1,Good\nhttps://example.com/live", metadata)
        assertEquals("Good", channels.single().name)
    }

    @Test fun `favorites preserve explicit order and search folds accents`() {
        val channels = listOf(Channel("a", "Télé Sport", "FR", listOf("sports"), emptyList()),
            Channel("b", "News", "US", emptyList(), emptyList()))
        assertEquals(listOf("b", "a"), Catalog.browse(channels, Section.FAVORITES, listOf("missing", "b", "a")).map { it.id })
        assertEquals("a", Catalog.browse(channels, Section.ALL, query = "tele").single().id)
        assertEquals(listOf(Section.FAVORITES, Section.FRANCE, Section.USA, Section.SPORTS, Section.ALL), Section.entries)
    }

    @Test fun `identity survives URL rotation when provider id exists and fallback never merges distinct query streams`() {
        fun parse(url: String) = Catalog.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"France2.fr@HD\",France 2\n$url",metadata).single()
        assertEquals(parse("https://example.com/a?token=old").id, parse("https://example.com/a?token=new").id)
        val raw = "#EXTM3U\n#EXTINF:-1,Same\nhttps://example.com/live?id=1\n#EXTINF:-1,Same\nhttps://example.com/live?id=2"
        assertEquals(2, Catalog.parse(raw, emptyMap()).size)
    }
}
