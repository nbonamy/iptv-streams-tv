package fr.bonamy.iptv.core

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class Metadata(val id: String, val name: String, val country: String, val categories: List<String>)
data class Stream(val url: String, val label: String, val headers: Map<String, String> = emptyMap())
data class Channel(val id: String, val name: String, val country: String?, val categories: List<String>, val streams: List<Stream>, val logoUrl: String? = null)
enum class Section(val title: String) { FAVORITES("Favorites"), FRANCE("France"), USA("USA"), SPORTS("Sports"), ALL("All channels") }

object Catalog {
    private val combiningMarks = Regex("\\p{M}+")
    private val hex = "0123456789abcdef".toCharArray()
    /** Skip unused provider fields without building a full JSON object tree. */
    fun metadata(json: String): Map<String, Metadata> = JsonReader(StringReader(json)).use { reader ->
        val result = linkedMapOf<String, Metadata>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            var id = ""; var name = ""; var country = ""; val categories = mutableListOf<String>()
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (reader.peek() == JsonToken.NULL) { reader.nextNull(); continue }
                when (key) {
                    "id" -> id = reader.nextString()
                    "name" -> name = reader.nextString()
                    "country" -> country = reader.nextString().uppercase(Locale.ROOT).let { if (it == "UK") "GB" else it }
                    "categories" -> { reader.beginArray(); while (reader.hasNext()) categories.add(reader.nextString().lowercase(Locale.ROOT)); reader.endArray() }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (id.isNotBlank()) result[id] = Metadata(id, name, country, categories)
        }
        reader.endArray()
        require(result.isNotEmpty()) { "Empty channel metadata" }
        result
    }

    fun compactMetadata(json: String): String = Gson().toJson(metadata(json).values)

    fun parse(text: String, metadata: Map<String, Metadata>): List<Channel> {
        require(text.length <= 32 * 1024 * 1024) { "Playlist too large" }
        require(text.trimStart('\uFEFF', ' ', '\r', '\n', '\t').startsWith("#EXTM3U")) { "Invalid playlist" }
        val channels = linkedMapOf<String, Channel>()
        var attributes = emptyMap<String, String>()
        var name: String? = null
        var headers = linkedMapOf<String, String>()
        val attributePattern = Regex("([\\w-]+)\\s*=\\s*\"([^\"]*)\"")
        fun header(key: String, value: String) {
            val canonical = when (key.lowercase(Locale.ROOT)) {
                "http-user-agent", "user-agent" -> "User-Agent"
                "http-referrer", "http-referer", "referrer", "referer" -> "Referer"
                "origin" -> "Origin"
                else -> return
            }
            if (value.isNotBlank() && value.none { it == '\r' || it == '\n' }) headers[canonical] = value
        }
        text.lineSequence().forEach { raw ->
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.startsWith("#EXTINF:") -> {
                    attributes = attributePattern.findAll(line).associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] }
                    var quoted = false
                    val comma = line.indexOfFirst { char -> if (char == '"') quoted = !quoted; char == ',' && !quoted }
                    name = if (comma >= 0) line.substring(comma + 1).trim().ifEmpty { attributes["tvg-name"] } else null
                    headers = linkedMapOf()
                    attributes.forEach { (key, value) -> header(key, value) }
                }
                line.startsWith("#EXTVLCOPT:") -> {
                    val option = line.substringAfter(':')
                    if ('=' in option) header(option.substringBefore('='), option.substringAfter('='))
                }
                line.isNotBlank() && !line.startsWith('#') && name != null -> {
                    val url = line.substringBefore('|').trim()
                    val uri = runCatching { URI(url) }.getOrNull()
                    if (uri?.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri?.host.isNullOrBlank()) {
                        line.substringAfter('|', "").split('&').filter { '=' in it }.forEach { option ->
                            runCatching { header(option.substringBefore('='), URLDecoder.decode(option.substringAfter('='), "UTF-8")) }
                        }
                        val providerId = attributes["tvg-id"].orEmpty().substringBefore('@').trim()
                        val id = providerId.ifBlank {
                            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
                            "url:" + buildString(digest.size * 2) { digest.forEach { byte -> val value = byte.toInt() and 255; append(hex[value ushr 4]); append(hex[value and 15]) } }
                        }
                        val info = metadata[providerId]
                        val categories = (info?.categories.orEmpty() + attributes["group-title"].orEmpty().split(';').map { it.trim().lowercase(Locale.ROOT) }).filter { it.isNotBlank() }.distinct()
                        val existing = channels[id]
                        val stream = Stream(url, name!!, headers.toMap())
                        channels[id] = Channel(id, info?.name?.ifBlank { null } ?: name!!,
                            info?.country?.ifBlank { null }, (existing?.categories.orEmpty() + categories).distinct(),
                            (existing?.streams.orEmpty() + stream).distinctBy { it.url to it.headers },
                            existing?.logoUrl ?: attributes["tvg-logo"]?.trim()?.takeIf { logo ->
                                runCatching { URI(logo).let { it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !it.host.isNullOrBlank() } }.getOrDefault(false)
                            })
                    }
                    name = null
                    headers = linkedMapOf()
                }
            }
        }
        require(channels.isNotEmpty()) { "Playlist contains no supported channels" }
        // Compute Unicode sort keys once, not on every comparison in the sort.
        return channels.values.map { it to folded(it.name) }.sortedWith(compareBy({ it.second }, { it.first.id })).map { it.first }
    }

    fun browse(channels: List<Channel>, section: Section, favorites: List<String> = emptyList(), query: String = "", country: String? = null, order: CountryChannelOrder = CountryChannelOrder.EMPTY): List<Channel> {
        val favoriteOrder = favorites.withIndex().associate { it.value to it.index }
        val terms = folded(query).split(' ').filter { it.isNotBlank() }
        val result = channels.filter { channel ->
            val inSection = when (section) {
                Section.FAVORITES -> channel.id in favoriteOrder
                Section.FRANCE -> channel.country == "FR"
                Section.USA -> channel.country == "US"
                Section.SPORTS -> "sports" in channel.categories
                Section.ALL -> true
            }
            inSection && (country == null || (channel.country ?: "ZZ") == country) &&
                (terms.isEmpty() || folded(searchableText(channel)).let { searchable -> terms.all { it in searchable } })
        }
        if (section == Section.FAVORITES) return result.sortedBy { favoriteOrder[it.id] }
        val orderCountry = country ?: when (section) { Section.FRANCE -> "FR"; Section.USA -> "US"; else -> null }
        return if (orderCountry != null) order.sort(result, orderCountry) else result
    }

    internal fun searchableText(channel: Channel) = channel.name + " " + channel.country.orEmpty() + " " + channel.categories.joinToString(" ")
    internal fun folded(value: String): String = if (value.all { it.code < 128 }) value.lowercase(Locale.ROOT)
        else Normalizer.normalize(value, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase(Locale.ROOT)
}
