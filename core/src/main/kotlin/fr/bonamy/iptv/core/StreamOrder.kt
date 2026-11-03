package fr.bonamy.iptv.core

/** Quality is source metadata, not a claim about a stream's current availability. */
object StreamOrder {
    private val dimensions = Regex("(?i)\\b(2160|1440|1080|720|576|540|480|360|240)[pi]\\b")
    private val uhd = Regex("(?i)\\b(?:UHD|4K|Ultra\\s*HD)\\b")
    private val fhd = Regex("(?i)\\b(?:FHD|Full\\s*HD)\\b")
    private val hd = Regex("(?i)\\bHD\\b")
    private val sd = Regex("(?i)\\bSD\\b")

    fun resolution(label: String, hint: String = ""): Int {
        fun height(value: String): Int = dimensions.findAll(value).maxOfOrNull { it.groupValues[1].toInt() }
            ?: when {
                uhd.containsMatchIn(value) -> 2160
                fhd.containsMatchIn(value) -> 1080
                hd.containsMatchIn(value) -> 720
                sd.containsMatchIn(value) -> 480
                else -> 0
            }
        return height(label).takeIf { it > 0 } ?: height(hint)
    }

    fun prefer(channel: Channel): Channel {
        if (channel.streams.size < 2) return channel
        val streams = channel.streams.sortedByDescending { stream ->
            stream.qualityHeight.takeIf { it > 0 } ?: resolution(stream.label)
        }
        return if (streams == channel.streams) channel else channel.copy(streams = streams)
    }
}
