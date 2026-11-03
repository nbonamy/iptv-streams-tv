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
    @Test fun `repeated provider slots use the lowest channel number regardless of input order`() {
        val order = CountryChannelOrder.parse("""{"countries":{"US":{"channels":[{"number":173,"name":"ESPN HD","ids":["espn"]},{"number":3,"name":"ESPN","ids":["espn"]},{"number":24,"name":"FX","ids":["fx"]}]}}}""")
        val items = listOf(channel("fx", "FX", "US"), channel("espn", "ESPN", "US"))
        assertEquals(listOf("espn", "fx"), order.sort(items, "US").map { it.id })
    }
    @Test fun `plus channels remain distinct in exact name fallback`() {
        val order = CountryChannelOrder.parse("""{"countries":{"US":{"channels":[{"number":43,"name":"AMC"},{"number":170,"name":"AMC+"}]}}}""")
        val items = listOf(channel("plus", "AMC+", "US"), channel("amc", "AMC", "US"))
        assertEquals(listOf("amc", "plus"), order.sort(items, "US").map { it.id })
    }
    @Test fun `US Chicago lineup ranks available identities and preserves unlisted and favorite channels`() {
        val json = javaClass.getResourceAsStream("/channel-order.json")!!.bufferedReader().use { it.readText() }
        val order = CountryChannelOrder.parse(json)
        val items = listOf(channel("other", "AAA Independent", "US"), channel("CNN.us", "CNN", "US"),
            channel("FX.us", "FX", "US"), channel("ESPN2.us", "ESPN 2", "US"), channel("ESPN.us", "ESPN", "US"))
        assertEquals(listOf("ESPN.us", "FX.us", "ESPN2.us", "CNN.us", "other"), order.sort(items, "US").map { it.id })
        assertEquals(items.toSet(), order.sort(items, "US").toSet())
        assertEquals(listOf("CNN.us", "ESPN.us"), Catalog.browse(items, Section.FAVORITES,
            listOf("CNN.us", "ESPN.us"), order = order).map { it.id })
    }
    @Test fun `bundled US channels follow the captured Xfinity lineup rather than alphabetical order`() {
        fun resource(name: String) = javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() }
        val channels = Catalog.parse(resource("worldwide.m3u"), Catalog.metadata(resource("channels.json")))
        val index = CatalogIndex(channels, CountryChannelOrder.parse(resource("channel-order.json")))
        assertEquals(listOf("ESPN.us", "TLNMediaChicago.us", "HSN.us", "CANTV19.us", "CANTV21.us", "FX.us"),
            index.country("US").take(6).map { it.id })
        assertEquals(channels.filter { it.country == "US" }.toSet(), index.country("US").toSet())
    }
    @Test fun `UK England lineup keeps core services first and regional alternatives afterward`() {
        val json = javaClass.getResourceAsStream("/channel-order.json")!!.bufferedReader().use { it.readText() }
        val order = CountryChannelOrder.parse(json)
        val items = listOf(channel("STV.uk", "STV", "GB"), channel("S4C.uk", "S4C", "GB"),
            channel("Channel5.uk", "Channel 5", "GB"), channel("ITV1.uk", "ITV1", "GB"),
            channel("BBCTwo.uk", "BBC Two", "GB"), channel("BBCOne.uk", "BBC One", "GB"),
            channel("Channel4.uk", "Channel 4", "GB"))
        val sorted = order.sort(items, "GB")
        assertEquals(listOf("BBCOne.uk", "BBCTwo.uk", "ITV1.uk", "Channel4.uk", "Channel5.uk"), sorted.take(5).map { it.id })
        assertEquals(setOf("STV.uk", "S4C.uk"), sorted.drop(5).map { it.id }.toSet())
        assertEquals(listOf("STV.uk", "BBCOne.uk"), Catalog.browse(items, Section.FAVORITES,
            listOf("STV.uk", "BBCOne.uk"), order = order).map { it.id })
    }
    @Test fun `UK ordering uses only available streams and retains every bundled UK channel`() {
        fun resource(name: String) = javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() }
        val channels = Catalog.parse(resource("worldwide.m3u"), Catalog.metadata(resource("channels.json")))
        val index = CatalogIndex(channels, CountryChannelOrder.parse(resource("channel-order.json")))
        assertEquals(listOf("BBCOne.uk", "BBCTwo.uk", "Channel4.uk", "Channel5.uk", "ITV2.uk", "BBCFour.uk"),
            index.country("GB").take(6).map { it.id })
        assertEquals(channels.filter { it.country == "GB" }.toSet(), index.country("GB").toSet())
        assertEquals(channels.filter { "bbc" in it.name.lowercase() }, index.search("bbc"))
    }
}
