package fr.bonamy.iptv

import android.content.Context
import android.util.AtomicFile
import fr.bonamy.iptv.core.*
import org.json.JSONArray
import java.io.File
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.gson.Gson

internal data class Library(val playlist: String, val metadataJson: String) {
    fun channels() = Catalog.parse(playlist, Catalog.metadata(metadataJson))
}

/** Channel data is ready immediately; the retained M3U/metadata is read only for Refresh. */
internal class CatalogSnapshot(val channels: List<Channel>, source: () -> Library) {
    val source: Library by lazy(source)
}

internal class LibraryStore(private val context: Context, private val fetch: (String) -> String = { sourceClient.fetch(it) }) {
    companion object {
        private val cacheLock = Any()
        private val sourceClient by lazy { SourceClient() }
    }
    private val file = AtomicFile(File(context.filesDir, "library.json"))
    @Volatile private var snapshot: CatalogSnapshot? = null
    @Volatile private var pendingSeed: CatalogSnapshot? = null
    private val gson = Gson()

    fun initial(): CatalogSnapshot {
        snapshot?.let { return it }
        return synchronized(cacheLock) {
            snapshot?.let { return@synchronized it }
            val loaded = runCatching { readPrepared(file.openRead().bufferedReader()) { synchronized(cacheLock) { readSource() } } }.getOrNull() ?: runCatching {
                // Migrate a text-only or damaged derived cache using its original saved source.
                val source = readSource()
                val channels = source.channels()
                write(source, channels)
                CatalogSnapshot(channels) { source }
            }.getOrElse {
                readPrepared(context.assets.open("catalog.json").bufferedReader()) {
                    Library(context.assets.open("worldwide.m3u").bufferedReader().use { it.readText() },
                        context.assets.open("channels.json").bufferedReader().use { it.readText() })
                }.also { pendingSeed = it }
            }
            loaded.also { snapshot = it }
        }
    }

    private fun readPrepared(input: java.io.Reader, source: () -> Library): CatalogSnapshot = JsonReader(input).use { reader ->
        var version = 0
        reader.beginObject()
        while (reader.hasNext()) when (reader.nextName()) {
            "version" -> version = reader.nextInt()
            "channels" -> {
                require(version in 2..3)
                val channels: Array<Channel> = gson.fromJson(reader, Array<Channel>::class.java)
                require(channels.isNotEmpty() && channels.all(::validChannel))
                // Parsed channels precede the larger source strings in the current format.
                // Closing here avoids decoding those strings on every cold process launch.
                return CatalogSnapshot(channels.toList(), source)
            }
            else -> reader.skipValue()
        }
        error("No prepared channels")
    }

    private fun readSource(): Library = JsonReader(file.openRead().bufferedReader()).use { reader ->
        var playlist: String? = null; var metadata: String? = null
        reader.beginObject()
        while (reader.hasNext()) when (reader.nextName()) {
            "playlist" -> playlist = reader.nextString()
            "metadata" -> metadata = reader.nextString()
            else -> reader.skipValue()
        }
        reader.endObject()
        Library(requireNotNull(playlist), requireNotNull(metadata))
    }

    private fun seedBundled() {
        val output = file.startWrite()
        try {
            context.assets.open("catalog.json").use { it.copyTo(output) }
            file.finishWrite(output)
        } catch (error: Exception) { file.failWrite(output); throw error }
    }

    /** Persist a bundled seed after the UI has received its in-memory catalog. */
    fun persistIfNeeded() {
        val expected = pendingSeed ?: return
        synchronized(cacheLock) {
            if (snapshot === expected && pendingSeed === expected) {
                seedBundled()
                pendingSeed = null
            }
        }
    }

    fun refresh(current: Library? = null): CatalogSnapshot {
        val playlist = fetch(Source.PLAYLIST)
        val metadata = runCatching { Catalog.compactMetadata(fetch(Source.METADATA)) }.getOrElse {
            runCatching { current?.metadataJson ?: snapshot?.source?.metadataJson }.getOrNull()
                ?: context.assets.open("channels.json").bufferedReader().use { it.readText() }
        }
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
        val updated = Library(playlist, metadata)
        val channels = updated.channels()
        return synchronized(cacheLock) {
            write(updated, channels)
            pendingSeed = null
            CatalogSnapshot(channels) { updated }.also { snapshot = it }
        }
    }

    private fun validChannel(channel: Channel): Boolean = runCatching {
        channel.id.isNotBlank() && channel.name.isNotBlank() && channel.categories.all { it.isNotBlank() } &&
            channel.streams.isNotEmpty() && channel.streams.all { it.url.isNotBlank() && it.label.isNotBlank() && it.headers.keys.all { key -> key.isNotBlank() } }
    }.getOrDefault(false)

    private fun write(library: Library, channels: List<Channel>) {
        val output = file.startWrite()
        try {
            val writer = JsonWriter(output.bufferedWriter())
            writer.beginObject().name("version").value(3).name("channels")
            gson.toJson(channels, List::class.java, writer)
            writer.name("playlist").value(library.playlist).name("metadata").value(library.metadataJson).endObject()
            writer.flush()
            file.finishWrite(output)
        } catch (error: Exception) { file.failWrite(output); throw error }
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
    fun favoriteChannels(channels: Map<String, Channel>): List<Channel> = favorites().map { id ->
        channels[id] ?: Channel(id, prefs.getString("name:$id", "Unavailable channel")!!, null, emptyList(), emptyList())
    }
}
