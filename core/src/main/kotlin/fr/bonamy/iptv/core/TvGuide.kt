package fr.bonamy.iptv.core

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory

data class GuideChannel(val id: String, val name: String) {
    fun match(channels: List<Channel>): Channel? {
        val aliases = mapOf("NT1.fr" to "TFX.fr", "Numero23.fr" to "RMCStory.fr", "Cherie25.fr" to "RMCLife.fr", "LEquipe21.fr" to "LEquipe.fr",
            "Arte.fr" to "arte.fr", "FranceInfo.fr" to "Franceinfo.fr", "LaChaineParlementaire.fr" to "LCP.fr",
            "CanalPlusCinema.fr" to "CanalPlusCinemas.fr")
        return channels.firstOrNull { it.id == id } ?: channels.firstOrNull { it.id == aliases[id] }
    }
}
data class GuideProgram(val channelId: String, val title: String, val description: String, val start: Long, val end: Long)
data class TvGuide(val channels: List<GuideChannel>, val programs: List<GuideProgram>) {
    companion object {
        const val URL = "https://xmltvfr.fr/xmltv/xmltv_tnt.xml"
        private val timestamp = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withResolverStyle(java.time.format.ResolverStyle.STRICT)
        fun parse(xml: String): TvGuide {
            require(xml.length <= 32 * 1024 * 1024)
            // XMLTV declares an external DTD. Accept the declaration, never load it;
            // reject internal entities before parsing to prevent expansion attacks.
            require(!xml.contains("<!ENTITY", ignoreCase = true))
            val channels = mutableListOf<GuideChannel>()
            val programs = mutableListOf<GuideProgram>()
            val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = false }.newSAXParser().xmlReader
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
            val handler = object : DefaultHandler() {
                var channelId = ""; var name = ""; var programChannel = ""
                var start: Long? = null; var end: Long? = null
                var title = ""; var description = ""; var field = ""
                val text = StringBuilder()
                override fun startElement(uri: String?, local: String?, q: String, a: Attributes) {
                    if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                    when (q) {
                        "channel" -> { channelId = a.getValue("id").orEmpty(); name = "" }
                        "programme" -> {
                            programChannel = a.getValue("channel").orEmpty(); title = ""; description = ""
                            fun time(key: String) = runCatching { OffsetDateTime.parse(a.getValue(key).trim(), timestamp).toInstant().toEpochMilli() }.getOrNull()
                            start = time("start"); end = time("stop")
                        }
                        "display-name", "title", "desc" -> { field = q; text.setLength(0) }
                    }
                }
                override fun characters(ch: CharArray, offset: Int, length: Int) { if (field.isNotEmpty()) text.append(ch, offset, length) }
                override fun endElement(uri: String?, local: String?, q: String) {
                    if (q == field) {
                        when (q) { "display-name" -> if (name.isEmpty()) name = text.toString().trim(); "title" -> if (title.isEmpty()) title = text.toString().trim(); "desc" -> if (description.isEmpty()) description = text.toString().trim() }
                        field = ""
                    }
                    if (q == "channel" && channelId.isNotBlank()) channels.add(GuideChannel(channelId, name.ifBlank { channelId }))
                    if (q == "programme") {
                        val s = start; val e = end
                        if (s != null && e != null && e > s && title.isNotBlank() && programChannel.isNotBlank()) programs.add(GuideProgram(programChannel, title, description, s, e))
                    }
                }
            }
            reader.contentHandler = handler; reader.errorHandler = handler
            reader.parse(InputSource(StringReader(xml)))
            require(channels.isNotEmpty() && programs.isNotEmpty()) { "Empty schedule" }
            return TvGuide(channels.distinctBy { it.id }, programs.distinct().sortedBy { it.start })
        }
    }
}
