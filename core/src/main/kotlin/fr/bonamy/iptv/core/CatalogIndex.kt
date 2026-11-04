package fr.bonamy.iptv.core

/** Immutable browsing data, prepared once when a local catalog loads or Refresh succeeds. */
class CatalogIndex(channels: List<Channel>, order: CountryChannelOrder = CountryChannelOrder.EMPTY) {
    val channels = channels.map { StreamOrder.prefer(order.overrideSources(it)) }.let { if (it == channels) channels else it }
    val byId = this.channels.associateBy { it.id }
    private val byCountry = this.channels.groupBy { it.country ?: "ZZ" }.mapValues { (code, items) -> order.sort(items, code) }
    val countries: Set<String> = byCountry.keys
    private val sports = this.channels.filter { "sports" in it.categories }
    private val searchRows = this.channels.map { it to Catalog.folded(Catalog.searchableText(it)) }

    fun country(code: String): List<Channel> = byCountry[code].orEmpty()
    fun browse(section: Section, country: String? = null): List<Channel> = when (section) {
        Section.FRANCE -> country("FR")
        Section.USA -> country("US")
        Section.SPORTS -> sports
        Section.ALL -> country?.let(::country) ?: channels
        Section.FAVORITES -> error("Favorites use the viewer's saved order")
    }
    fun search(query: String): List<Channel> {
        val terms = Catalog.folded(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return searchRows.asSequence().filter { (_, text) -> terms.all { it in text } }.map { it.first }.toList()
    }
}
