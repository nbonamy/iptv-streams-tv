package fr.bonamy.iptv

import android.content.Context
import android.util.AtomicFile
import fr.bonamy.iptv.core.SourceClient
import fr.bonamy.iptv.core.TvGuide
import java.io.File

internal data class SavedGuide(val guide: TvGuide, val saved: Boolean)
internal class GuideStore(context: Context, private val fetch: () -> String = { SourceClient().fetch(TvGuide.URL) }) {
    private val file = AtomicFile(File(context.filesDir, "france-guide.xml"))
    private data class Snapshot(val guide: TvGuide, val modified: Long, val size: Long)
    private var snapshot: Snapshot? = null
    @Synchronized fun load(force: Boolean = false): SavedGuide {
        val base = file.baseFile
        val cached = snapshot?.takeIf { base.isFile && it.modified == base.lastModified() && it.size == base.length() }?.guide
            ?: runCatching { file.openRead().bufferedReader().use { TvGuide.parse(it.readText()) } }.getOrNull()?.also {
                snapshot = Snapshot(it, base.lastModified(), base.length())
            }
        val now = System.currentTimeMillis()
        val age = now - file.baseFile.lastModified()
        if (!force && cached != null && age in 0 until 60 * 60 * 1000 &&
            cached.programs.any { it.start <= now && it.end > now }) return SavedGuide(cached, false)
        try {
            val xml = fetch()
            val guide = TvGuide.parse(xml)
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val output = file.startWrite()
            try { output.write(xml.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
            snapshot = Snapshot(guide, file.baseFile.lastModified(), file.baseFile.length())
            return SavedGuide(guide, false)
        } catch (error: Exception) {
            if (Thread.currentThread().isInterrupted || cached == null) throw error
            return SavedGuide(cached, true)
        }
    }
}
