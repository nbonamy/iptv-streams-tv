package fr.bonamy.iptv.core

import kotlin.test.*

class StreamOrderTest {
    private fun stream(label: String) = Stream("https://example.com/$label", label)
    @Test fun `higher definition comes first while equivalent sources keep playlist order`() {
        val input = listOf("SD", "HD", "FHD", "UHD", "Other HD", "Unknown").map(::stream)
        val channel = Channel("a", "A", "US", emptyList(), input)
        assertEquals(listOf("UHD", "FHD", "HD", "Other HD", "SD", "Unknown"),
            StreamOrder.prefer(channel).streams.map { it.label })
        assertEquals(input.toSet(), StreamOrder.prefer(channel).streams.toSet())
    }
    @Test fun `resolution labels and source hints never interpret a bare channel number as quality`() {
        assertEquals(2160, StreamOrder.resolution("A (2160p)"))
        assertEquals(1080, StreamOrder.resolution("A Full HD"))
        assertEquals(1080, StreamOrder.resolution("A (1080i)"))
        assertEquals(720, StreamOrder.resolution("A", "HD"))
        assertEquals(576, StreamOrder.resolution("A (576p)"))
        assertEquals(0, StreamOrder.resolution("Channel 4", "WBBM"))
        assertEquals(0, StreamOrder.resolution("Shades of Blue"))
    }
    @Test fun `parser prefers HD even if SD appears first and label omits quality`() {
        val channels = Catalog.parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="a@SD",A
            https://example.com/sd
            #EXTINF:-1 tvg-id="a@HD",A
            https://example.com/hd
        """.trimIndent(), emptyMap())
        assertEquals(listOf("https://example.com/hd", "https://example.com/sd"), channels.single().streams.map { it.url })
        assertEquals(listOf("A", "A"), channels.single().streams.map { it.label })
    }
    @Test fun `old prepared caches are reordered in the index without reparsing or changing channel order`() {
        val sd = stream("A (480p)"); val hd = stream("A (1080p)")
        val channel = Channel("a", "A", "US", emptyList(), listOf(sd, hd))
        val other = channel.copy(id = "b", name = "B", streams = listOf(stream("B")))
        val index = CatalogIndex(listOf(channel, other))
        assertEquals(listOf("a", "b"), index.channels.map { it.id })
        assertEquals(listOf(hd, sd), index.byId.getValue("a").streams)
        assertSame(index.byId["a"], index.country("US").first())
        assertSame(index.byId["a"], index.search("A").first())
        assertEquals(listOf(sd, hd), channel.streams)
    }
    @Test fun `already preferred channels retain the same instance`() {
        val channel = Channel("a", "A", "US", emptyList(), listOf(stream("HD"), stream("SD")))
        assertSame(channel, StreamOrder.prefer(channel))
    }
}
