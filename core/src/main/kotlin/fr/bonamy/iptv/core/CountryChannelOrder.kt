package fr.bonamy.iptv.core

import com.google.gson.JsonParser
import java.text.Normalizer
import java.util.Locale

/** Country policies are data. Unlisted channels retain the catalog's original order. */
class CountryChannelOrder private constructor(private val rules: Map<String, Lineup>) {
    private data class Rank(val group: Int, val position: Int) : Comparable<Rank> {
        override fun compareTo(other: Rank) = compareValuesBy(this, other, Rank::group, Rank::position)
    }
    private data class Lineup(
        val ids: Map<String, Rank>, val names: Map<String, Rank>, val categories: Map<String, Rank>,
        val excludedIds: Set<String>, val retainCategories: Set<String>,
    )

    fun sort(channels: List<Channel>, country: String): List<Channel> {
        val policy = rules[country] ?: return channels
        val (ids, names, categories) = policy
        val unlisted = Rank(Int.MAX_VALUE, Int.MAX_VALUE)
        return channels.filter { channel ->
            channel.country != country || channel.id !in policy.excludedIds || channel.categories.any { it in policy.retainCategories }
        }.map { channel -> channel to (
            if (channel.country != country) unlisted
            else ids[channel.id] ?: names[normalized(channel.name)]
                ?: categories.entries.firstOrNull { it.key in channel.categories }?.value ?: unlisted
        ) }.sortedBy { it.second }.map { it.first }
    }

    companion object {
        val EMPTY = CountryChannelOrder(emptyMap())
        private val combiningMarks = Regex("\\p{M}+")
        private fun normalized(value: String): String {
            val folded = if (value.all { it.code < 128 }) value.lowercase(Locale.ROOT)
                else Normalizer.normalize(value, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase(Locale.ROOT)
            return folded.filter { it.isLetterOrDigit() || it == '+' }
        }

        fun parse(json: String): CountryChannelOrder {
            val countries = JsonParser.parseString(json).asJsonObject.getAsJsonObject("countries")
            return CountryChannelOrder(countries.entrySet().associate { (country, value) ->
                val policy = value.asJsonObject
                val ids = mutableMapOf<String, Rank>()
                val names = mutableMapOf<String, Rank>()
                val categories = mutableMapOf<String, Rank>()
                policy.getAsJsonArray("channels")?.forEach { item ->
                    val row = item.asJsonObject
                    val rank = Rank(0, row.get("number").asInt.also { require(it > 0) })
                    row.getAsJsonArray("ids")?.forEach { id -> ids.merge(id.asString, rank, ::minOf) }
                    (listOf(row.get("name").asString) + row.getAsJsonArray("aliases")?.map { it.asString }.orEmpty()).forEach { name ->
                        names.merge(normalized(name), rank, ::minOf)
                    }
                }
                val groups = policy.getAsJsonArray("groups")?.map { it.asJsonObject }.orEmpty()
                require(groups.isEmpty() || !policy.has("channels")) { "Choose a numbered or grouped lineup" }
                val keys = groups.map { it.get("key").asString }
                val priority = policy.getAsJsonArray("categoryPriority")?.map { it.asString } ?: keys
                require(keys.distinct().size == keys.size && priority.distinct().size == priority.size && keys.containsAll(priority))
                groups.forEachIndexed { group, row ->
                    row.getAsJsonArray("ids")?.forEachIndexed { position, id ->
                        require(ids.put(id.asString, Rank(group, position)) == null) { "Channel assigned to multiple groups" }
                    }
                }
                // Category precedence resolves mixed genres independently of their display order.
                (priority + keys.filter { it !in priority }).forEach { key ->
                    val group = keys.indexOf(key)
                    groups[group].getAsJsonArray("categories")?.forEach { category ->
                        categories.putIfAbsent(category.asString, Rank(group, Int.MAX_VALUE))
                    }
                }
                country.uppercase(Locale.ROOT) to Lineup(ids, names, categories,
                    policy.getAsJsonArray("excludedIds")?.map { it.asString }?.toSet().orEmpty(),
                    policy.getAsJsonArray("retainCategories")?.map { it.asString }?.toSet().orEmpty())
            })
        }
    }
}
