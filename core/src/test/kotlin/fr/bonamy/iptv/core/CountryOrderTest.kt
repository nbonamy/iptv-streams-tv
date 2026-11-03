package fr.bonamy.iptv.core

import kotlin.test.*

class CountryOrderTest {
    private fun channel(id: String, name: String, country: String) = Channel(id, name, country, emptyList(), emptyList())
    @Test fun `country rules rank exact ids without changing another country or favorites`() {
        val order = CountryChannelOrder.parse("""{"countries":{"AA":{"channels":[{"number":1,"name":"First","ids":["first"]},{"number":2,"name":"Second","ids":["second"]}]}}}""")
        val items = listOf(channel("extra", "Aardvark", "AA"), channel("second", "Z Second", "AA"), channel("first", "Z First", "AA"))
        assertEquals(listOf("first", "second", "extra"), order.sort(items, "AA").map { it.id })
        assertEquals(items, order.sort(items, "BB"))
        assertEquals(listOf("second", "first"), Catalog.browse(items, Section.FAVORITES, listOf("second", "first"), order = order).map { it.id })
    }
    @Test fun `name fallback is exact normalized and scoped to the country`() {
        val order = CountryChannelOrder.parse("""{"countries":{"AA":{"channels":[{"number":1,"name":"Télé One"}]}}}""")
        val items = listOf(channel("extra", "Aardvark", "AA"), channel("spin", "Tele One Extra", "AA"), channel("one", "TELE ONE", "AA"), channel("foreign", "Télé One", "BB"))
        assertEquals(listOf("one", "extra", "spin", "foreign"), order.sort(items, "AA").map { it.id })
        assertEquals(items.toSet(), order.sort(items, "AA").toSet())
    }
    @Test fun `France data starts TF1 and France 2 and does not create missing channels`() {
        val json = javaClass.getResourceAsStream("/channel-order.json")!!.bufferedReader().use { it.readText() }
        val order = CountryChannelOrder.parse(json)
        val items = listOf(channel("other", "AAA Local", "FR"), channel("France2.fr", "France 2", "FR"), channel("TF1.fr", "TF1", "FR"))
        assertEquals(listOf("TF1.fr", "France2.fr", "other"), order.sort(items, "FR").map { it.id })
    }
}
