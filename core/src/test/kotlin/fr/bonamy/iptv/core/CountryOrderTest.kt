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
    @Test fun `US curated lineup keeps mainstream first and preserves unlisted and favorite channels`() {
        val json = javaClass.getResourceAsStream("/channel-order.json")!!.bufferedReader().use { it.readText() }
        val order = CountryChannelOrder.parse(json)
        val items = listOf(channel("other", "AAA Independent", "US"), channel("CNN.us", "CNN", "US"),
            channel("FX.us", "FX", "US"), channel("ESPN2.us", "ESPN 2", "US"), channel("ESPN.us", "ESPN", "US"))
        assertEquals(listOf("CNN.us", "FX.us", "ESPN.us", "ESPN2.us", "other"), order.sort(items, "US").map { it.id })
        assertEquals(items.toSet(), order.sort(items, "US").toSet())
        assertEquals(listOf("CNN.us", "ESPN.us"), Catalog.browse(items, Section.FAVORITES,
            listOf("CNN.us", "ESPN.us"), order = order).map { it.id })
    }
    @Test fun `bundled US channels follow curated groups rather than cable channel numbers`() {
        fun resource(name: String) = javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() }
        val channels = Catalog.parse(resource("worldwide.m3u"), Catalog.metadata(resource("channels.json")))
        val index = CatalogIndex(channels, CountryChannelOrder.parse(resource("channel-order.json")))
        assertEquals(listOf("CBS.us", "NBC.us", "ABC.us", "Fox.us", "PBS.us", "CNN.us"),
            index.country("US").take(6).map { it.id })
        val policy = com.google.gson.JsonParser.parseString(resource("channel-order.json")).asJsonObject.getAsJsonObject("countries").getAsJsonObject("US")
        val excluded = policy.getAsJsonArray("excludedIds").map { it.asString }.toSet()
        assertEquals(channels.filter { it.country == "US" && (it.id !in excluded || "sports" in it.categories) }.map { it.id }.toSet(), index.country("US").map { it.id }.toSet())
        assertFalse(index.country("US").any { it.id == "Univision.us" })
        assertTrue(index.country("US").any { it.id == "ESPNDeportes.us" })
        assertTrue(index.search("Univision").any { it.id == "Univision.us" })
    }
    @Test fun `group categories are continuous with explicit placement and specific genre precedence`() {
        val order = CountryChannelOrder.parse("""{"countries":{"US":{
            "categoryPriority":["kids","sports","news","factual","movies","shows"],
            "groups":[
                {"key":"mainstream","ids":["cbs"]},
                {"key":"news","categories":["news"]},
                {"key":"movies","categories":["movies"]},
                {"key":"shows","ids":["usa"],"categories":["series","entertainment"]},
                {"key":"factual","ids":["discovery"],"categories":["documentary","education"]},
                {"key":"sports","categories":["sports"]},
                {"key":"kids","categories":["kids"]}
            ]}}}""")
        fun item(id: String, vararg categories: String) = Channel(id, id, "US", categories.toList(), emptyList())
        val items = listOf(item("unknown"), item("kids", "movies", "education", "kids"),
            item("sports", "news", "sports"), item("usa", "movies", "series", "sports"),
            item("discovery", "entertainment"), item("movie", "movies", "series"),
            item("news", "news"), item("show", "series"), item("cbs", "general"))
        assertEquals(listOf("cbs", "news", "movie", "usa", "show", "discovery", "sports", "kids", "unknown"),
            order.sort(items, "US").map { it.id })
        assertEquals(items, order.sort(items, "CA"))
        assertEquals(listOf("kids", "cbs"), Catalog.browse(items, Section.FAVORITES,
            listOf("kids", "cbs"), order = order).map { it.id })
    }
    @Test fun `US factual overrides and all eight groups retain their display order`() {
        fun resource(name: String) = javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() }
        val order = CountryChannelOrder.parse(resource("channel-order.json"))
        val metadata = Catalog.metadata(resource("channels.json"))
        val expected = listOf("CBS.us", "CNN.us", "HBO.us", "USANetwork.us", "MTV.us", "DiscoveryChannel.us", "ESPN.us", "PBSKids.us", "AdultSwimRickandMorty.us")
        val items = expected.reversed().map { id ->
            val row = metadata.getValue(id)
            Channel(id, row.name, row.country, row.categories, emptyList())
        }
        assertEquals(expected, order.sort(items, "US").map { it.id })
    }
    @Test fun `language exclusions affect only country browsing and retain sports and unknown channels`() {
        val order = CountryChannelOrder.parse("""{"countries":{"US":{
            "channels":[],"excludedIds":["spanish","sports"],"retainCategories":["sports"]
        }}}""")
        val items = listOf(channel("spanish", "Spanish", "US"),
            channel("sports", "Sports", "US").copy(categories = listOf("sports")),
            channel("unknown", "Unknown", "US"))
        assertEquals(listOf("sports", "unknown"), order.sort(items, "US").map { it.id })
        assertEquals(items, order.sort(items, "CA"))
        assertEquals(items, CatalogIndex(items, order).search("US"))
        assertEquals(listOf("spanish"), Catalog.browse(items, Section.FAVORITES, listOf("spanish"), order = order).map { it.id })
    }
    @Test fun `focused US networks lead every genre with obscure and non Chicago news in an alphabetical tail`() {
        fun resource(name: String) = javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() }
        val json = resource("channel-order.json")
        val policy = com.google.gson.JsonParser.parseString(json).asJsonObject.getAsJsonObject("countries").getAsJsonObject("US")
        val focused = policy.getAsJsonArray("groups").flatMap { row -> row.asJsonObject.getAsJsonArray("ids").map { it.asString } }
        assertEquals(focused.size, focused.distinct().size)
        val channels = Catalog.parse(resource("worldwide.m3u"), Catalog.metadata(resource("channels.json")))
        val items = CatalogIndex(channels, CountryChannelOrder.parse(json)).country("US")
        val present = focused.filter { id -> items.any { it.id == id } }
        assertEquals(present, items.take(present.size).map { it.id })
        for (id in listOf("FlicksofFury.us", "MegaExtrenoHD.us", "CBSNewsNewYork.us", "ABCNewsLive2.us")) {
            assertTrue(items.indexOfFirst { it.id == id } >= present.size, "$id should follow the focused lineup")
        }
        assertTrue(items.indexOfFirst { it.id == "CBSNewsChicago.us" } < present.size)
        val tail = items.drop(present.size)
        assertEquals(channels.filter { channel -> tail.any { it.id == channel.id } }.map { it.id }, tail.map { it.id })
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
