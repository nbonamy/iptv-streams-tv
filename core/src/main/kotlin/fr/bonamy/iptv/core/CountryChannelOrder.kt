package fr.bonamy.iptv.core

import com.google.gson.JsonParser
import java.text.Normalizer
import java.util.Locale

/** Country policies are data. Unlisted channels retain the catalog's original order. */
class CountryChannelOrder private constructor(private val rules: Map<String, Lineup>) {
    private data class Lineup(val ids: Map<String, Int>, val names: Map<String, Int>)

    fun sort(channels: List<Channel>, country: String): List<Channel> {
        val (ids, names) = rules[country] ?: return channels
        return channels.map { channel -> channel to (
            if (channel.country != country) Int.MAX_VALUE
            else ids[channel.id] ?: names[normalized(channel.name)] ?: Int.MAX_VALUE
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
                val ids = mutableMapOf<String, Int>()
                val names = mutableMapOf<String, Int>()
                value.asJsonObject.getAsJsonArray("channels").forEach { item ->
                    val row = item.asJsonObject
                    val number = row.get("number").asInt.also { require(it > 0) }
                    row.getAsJsonArray("ids")?.forEach { id -> ids.merge(id.asString, number, ::minOf) }
                    (listOf(row.get("name").asString) + row.getAsJsonArray("aliases")?.map { it.asString }.orEmpty()).forEach { name ->
                        names.merge(normalized(name), number, ::minOf)
                    }
                }
                country.uppercase(Locale.ROOT) to Lineup(ids, names)
            })
        }
    }
}
