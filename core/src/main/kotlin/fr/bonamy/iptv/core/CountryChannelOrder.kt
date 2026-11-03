package fr.bonamy.iptv.core

import com.google.gson.JsonParser
import java.text.Normalizer
import java.util.Locale

/** Country policies are data. Unlisted channels retain the catalog's original order. */
class CountryChannelOrder private constructor(private val rules: Map<String, List<Rule>>) {
    private data class Rule(val number: Int, val ids: Set<String>, val names: Set<String>)

    fun sort(channels: List<Channel>, country: String): List<Channel> {
        val entries = rules[country] ?: return channels
        val ids = entries.flatMap { rule -> rule.ids.map { it to rule.number } }.toMap()
        val names = entries.flatMap { rule -> rule.names.map { it to rule.number } }.toMap()
        return channels.sortedBy { channel ->
            if (channel.country != country) Int.MAX_VALUE
            else ids[channel.id] ?: names[normalized(channel.name)] ?: Int.MAX_VALUE
        }
    }

    companion object {
        val EMPTY = CountryChannelOrder(emptyMap())
        private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        fun parse(json: String): CountryChannelOrder {
            val countries = JsonParser.parseString(json).asJsonObject.getAsJsonObject("countries")
            return CountryChannelOrder(countries.entrySet().associate { (country, value) ->
                country.uppercase(Locale.ROOT) to value.asJsonObject.getAsJsonArray("channels").map { item ->
                    val row = item.asJsonObject
                    val number = row.get("number").asInt.also { require(it > 0) }
                    val ids = row.getAsJsonArray("ids")?.map { it.asString }?.toSet().orEmpty()
                    val names = (listOf(row.get("name").asString) + row.getAsJsonArray("aliases")?.map { it.asString }.orEmpty()).map(::normalized).toSet()
                    Rule(number, ids, names)
                }
            })
        }
    }
}
