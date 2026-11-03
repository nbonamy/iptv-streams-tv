package fr.bonamy.iptv.core

import kotlin.test.*

class CatalogIndexTest {
    private val channels = listOf(
        Channel("a", "Actualités", "FR", listOf("news"), emptyList()),
        Channel("b", "BBC", "GB", emptyList(), emptyList()),
        Channel("c", "Sport", "FR", listOf("sports"), emptyList()),
        Channel("d", "Unknown", null, emptyList(), emptyList()),
    )
    @Test fun `countries reuse prepared lists and source channel instances`() {
        val index = CatalogIndex(channels)
        assertSame(index.country("FR"), index.country("FR"))
        assertSame(channels[0], index.byId["a"])
        assertEquals(listOf("a", "c"), index.browse(Section.FRANCE).map { it.id })
        assertEquals(listOf("b"), index.browse(Section.ALL, "GB").map { it.id })
        assertEquals(listOf("d"), index.country("ZZ").map { it.id })
        assertEquals(setOf("FR", "GB", "ZZ"), index.countries)
        assertSame(channels, index.browse(Section.ALL))
    }
    @Test fun `indexed search matches all terms with accents country and categories`() {
        val index = CatalogIndex(channels)
        for (query in listOf("actualites", "FR sports", "news", "BBC", "unknown", "nothing"))
            assertEquals(Catalog.browse(channels, Section.ALL, query = query), index.search(query))
        assertTrue(index.search(" ").isEmpty())
    }
    @Test fun `index applies country lineup without changing global alphabetical search`() {
        val order = CountryChannelOrder.parse("""{"countries":{"FR":{"channels":[{"number":1,"name":"Sport","ids":["c"]}]}}}""")
        val index = CatalogIndex(channels, order)
        assertEquals(listOf("c", "a"), index.country("FR").map { it.id })
        assertEquals(listOf("a", "c"), index.search("FR").map { it.id })
    }
}
