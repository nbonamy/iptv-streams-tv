package fr.bonamy.iptv

import android.content.Context
import android.util.AtomicFile
import fr.bonamy.iptv.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

internal data class Library(val playlist: String, val metadataJson: String) {
    fun channels() = Catalog.parse(playlist, Catalog.metadata(metadataJson))
}

internal class LibraryStore(private val context: Context, private val fetch: (String) -> String = SourceClient()::fetch) {
    companion object { private val cacheLock = Any() }
    private val file = AtomicFile(File(context.filesDir, "library.json"))
    fun bundled() = Library(context.assets.open("worldwide.m3u").bufferedReader().use { it.readText() },
        context.assets.open("channels.json").bufferedReader().use { it.readText() })

    fun initial(): Pair<Library, List<Channel>> = synchronized(cacheLock) {
        val cached = runCatching {
            JsonReader(file.openRead().bufferedReader()).use { reader ->
                var playlist: String? = null; var metadata: String? = null
                reader.beginObject()
                while (reader.hasNext()) when (reader.nextName()) {
                    "playlist" -> playlist = reader.nextString()
                    "metadata" -> metadata = reader.nextString()
                    else -> reader.skipValue()
                }
                reader.endObject()
                Library(requireNotNull(playlist), requireNotNull(metadata)).let { it to it.channels() }
            }
        }.getOrNull()
        cached ?: bundled().let { it to it.channels() }
    }

    fun refresh(current: Library): Pair<Library, List<Channel>> {
        val playlist = fetch(Source.PLAYLIST)
        val metadata = runCatching { Catalog.compactMetadata(fetch(Source.METADATA)) }.getOrDefault(current.metadataJson)
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
        val updated = Library(playlist, metadata)
        val channels = updated.channels() // Validate everything before replacing the working cache.
        synchronized(cacheLock) {
            val output = file.startWrite()
            try {
                // Stream escaping directly to disk: JSONObject.toString creates several
                // full-size copies and competes with the video decoder for the TV heap.
                val writer = JsonWriter(output.writer())
                writer.beginObject().name("playlist").value(playlist).name("metadata").value(metadata).endObject()
                writer.flush()
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        }
        return updated to channels
    }
}

internal class ViewerPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("viewer", Context.MODE_PRIVATE)
    private fun array() = runCatching { JSONArray(prefs.getString("favorites", "[]")) }.getOrDefault(JSONArray())
    fun favorites(): List<String> = array().let { a -> List(a.length()) { a.getString(it) } }.distinct()
    fun toggle(channel: Channel) {
        val ids = favorites().toMutableList()
        if (!ids.remove(channel.id)) ids.add(channel.id)
        prefs.edit().putString("favorites", JSONArray(ids).toString()).putString("name:${channel.id}", channel.name).apply()
    }
    fun move(id: String, delta: Int) {
        val ids = favorites().toMutableList(); val from = ids.indexOf(id); val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.removeAt(from); ids.add(to, id)
        prefs.edit().putString("favorites", JSONArray(ids).toString()).apply()
    }
    fun country(id: String, country: String?) { prefs.edit().putString("country:$id", country).apply() }
    fun sports(id: String, sports: Boolean?) {
        val edit = prefs.edit()
        if (sports == null) edit.remove("sports:$id") else edit.putBoolean("sports:$id", sports)
        edit.apply()
    }
    fun apply(channels: List<Channel>): List<Channel> {
        val present = channels.map { it.id }.toHashSet()
        val missing = favorites().filterNot { it in present }.map { Channel(it, prefs.getString("name:$it", "Unavailable channel")!!, null, emptyList(), emptyList()) }
        return (channels + missing).map { channel ->
            val categories = if (!prefs.contains("sports:${channel.id}")) channel.categories else
                channel.categories.filterNot { it == "sports" } + if (prefs.getBoolean("sports:${channel.id}", false)) listOf("sports") else emptyList()
            channel.copy(country = prefs.getString("country:${channel.id}", null) ?: channel.country, categories = categories)
        }
    }
}
