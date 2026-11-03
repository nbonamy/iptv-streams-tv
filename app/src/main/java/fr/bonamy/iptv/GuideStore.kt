package fr.bonamy.iptv

import android.content.Context
import android.util.AtomicFile
import fr.bonamy.iptv.core.SourceClient
import fr.bonamy.iptv.core.TvGuide
import java.io.File

internal data class SavedGuide(val guide: TvGuide, val saved: Boolean)
internal class GuideStore(context: Context, private val fetch: () -> String = { SourceClient().fetch(TvGuide.URL) }) {
    private val file = AtomicFile(File(context.filesDir, "france-guide.xml"))
    @Synchronized fun load(force: Boolean = false): SavedGuide {
        val cached = runCatching { file.openRead().bufferedReader().use { TvGuide.parse(it.readText()) } }.getOrNull()
        val age = System.currentTimeMillis() - file.baseFile.lastModified()
        if (!force && cached != null && age in 0 until 60 * 60 * 1000) return SavedGuide(cached, false)
        try {
            val xml = fetch()
            val guide = TvGuide.parse(xml)
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val output = file.startWrite()
            try { output.write(xml.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
            return SavedGuide(guide, false)
        } catch (error: Exception) {
            if (Thread.currentThread().isInterrupted || cached == null) throw error
            return SavedGuide(cached, true)
        }
    }
}
