package fr.bonamy.iptv.core

import com.google.gson.Gson
import java.io.File
import com.google.gson.stream.JsonWriter

/** Build-time preparation uses the exact same parser as a downloaded Refresh. */
object BundledCatalogCompiler {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3)
        val playlist = File(args[0]).readText()
        val metadata = File(args[1]).readText()
        val channels = Catalog.parse(playlist, Catalog.metadata(metadata))
        val output = File(args[2]).also { it.parentFile.mkdirs() }
        output.bufferedWriter().use { outputWriter ->
            val writer = JsonWriter(outputWriter)
            writer.beginObject().name("version").value(3).name("channels")
            Gson().toJson(channels, List::class.java, writer)
            writer.name("playlist").value(playlist).name("metadata").value(metadata).endObject()
            writer.flush()
        }
    }
}
