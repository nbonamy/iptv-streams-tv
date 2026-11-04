package fr.bonamy.iptv.core

/** Move within an already ordered browsing collection, retaining manual Favorites order. */
object ChannelNavigation {
    fun adjacent(channels: List<Channel>, currentId: String, direction: Int): Channel? {
        require(direction == -1 || direction == 1)
        if (channels.isEmpty()) return null
        val current = channels.indexOfFirst { it.id == currentId }
        val anchor = if (current >= 0) current else if (direction > 0) -1 else 0
        for (step in 1..channels.size) {
            val channel = channels[Math.floorMod(anchor + direction * step, channels.size)]
            if (channel.id != currentId && channel.streams.isNotEmpty()) return channel
        }
        return null
    }
}
