package fr.bonamy.iptv.core

import kotlin.test.*

class SourceOverrideTest {
    private val policy = """{"countries":{"US":{"sourceOverrides":{"NBC.us":[
        {"label":"NBC Chicago (1080p)","url":"https://example.com/wmaq.m3u8","headers":{"User-Agent":"Fixture"}}
    ]}}}}"""
    private fun channel(id: String) = Channel(id, id, "US", listOf("general"),
        listOf(Stream("https://example.com/original", "Original")), "https://example.com/logo.png")

    @Test fun `country source overrides replace only matching provider streams throughout the index`() {
        val original = channel("NBC.us")
        val other = channel("other.us")
        val index = CatalogIndex(listOf(original, other), CountryChannelOrder.parse(policy))
        val effective = index.byId.getValue("NBC.us")
        assertEquals(listOf(Stream("https://example.com/wmaq.m3u8", "NBC Chicago (1080p)", mapOf("User-Agent" to "Fixture"), 1080)), effective.streams)
        assertEquals(original, effective.copy(streams = original.streams))
        assertEquals("https://example.com/original", original.streams.single().url)
        assertSame(other, index.byId["other.us"])
        assertSame(effective, index.country("US").first())
        assertSame(effective, index.search("NBC").single())
        assertEquals(listOf("other.us", "NBC.us"), Catalog.browse(index.channels, Section.FAVORITES,
            listOf("other.us", "NBC.us")).map { it.id })
    }
    @Test fun `refresh retains its downloaded source while the same override applies after URL rotation`() {
        val order = CountryChannelOrder.parse(policy)
        for (version in listOf("old", "new")) {
            val raw = Catalog.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"NBC.us@HD\",NBC\nhttps://example.com/$version", emptyMap())
            assertEquals("https://example.com/$version", raw.single().streams.single().url)
            assertEquals("https://example.com/wmaq.m3u8", CatalogIndex(raw, order).byId.getValue("NBC.us").streams.single().url)
        }
    }
    @Test fun `overrides do not create channels or match unrelated names`() {
        val other = channel("not-nbc.us").copy(name = "NBC")
        val index = CatalogIndex(listOf(other), CountryChannelOrder.parse(policy))
        assertEquals(listOf(other), index.channels)
        assertNull(index.byId["NBC.us"])
    }
    @Test fun `empty unsafe and header injected source overrides are rejected`() {
        for (entry in listOf("[]", """[{"label":"Bad","url":"file:///private/movie"}]""",
            """[{"label":"Bad","url":"https://user:password@example.com/live"}]""",
            """[{"label":"Bad","url":"https://example.com/live","headers":{"User-Agent":"A\r\nInjected: yes"}}]""")) {
            assertFailsWith<IllegalArgumentException> {
                CountryChannelOrder.parse("""{"countries":{"US":{"sourceOverrides":{"NBC.us":$entry}}}}""")
            }
        }
    }
}
