package fr.bonamy.iptv.core

/** Give short sliding HLS windows room on both sides of the initial playback position. */
object LiveWindowPolicy {
    fun startPosition(windowMs: Long, providerDefaultMs: Long, segmentMs: Long): Long? {
        if (windowMs <= 0 || providerDefaultMs < 0 || segmentMs <= 0 || segmentMs > Long.MAX_VALUE / 6) return null
        if (windowMs < 2 * segmentMs || windowMs > 6 * segmentMs || providerDefaultMs >= segmentMs) return null
        return (windowMs / 2).takeIf { it > providerDefaultMs }
    }
}
