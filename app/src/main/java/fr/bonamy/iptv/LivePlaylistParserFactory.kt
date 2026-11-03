package fr.bonamy.iptv

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.upstream.ParsingLoadable
import fr.bonamy.iptv.core.LiveWindowPolicy

/** Set the safe default before Media3 chooses its first segment, rather than seeking afterward. */
@UnstableApi
internal class LivePlaylistParserFactory : HlsPlaylistParserFactory {
    private val delegate = DefaultHlsPlaylistParserFactory()
    override fun createPlaylistParser() = wrap(delegate.createPlaylistParser())
    override fun createPlaylistParser(multivariantPlaylist: HlsMultivariantPlaylist, previousMediaPlaylist: HlsMediaPlaylist?) =
        wrap(delegate.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist))

    private fun wrap(parser: ParsingLoadable.Parser<HlsPlaylist>) = ParsingLoadable.Parser<HlsPlaylist> { uri, input ->
        val parsed = parser.parse(uri, input)
        if (parsed !is HlsMediaPlaylist || parsed.hasEndTag || parsed.playlistType != HlsMediaPlaylist.PLAYLIST_TYPE_UNKNOWN ||
            parsed.startOffsetUs != C.TIME_UNSET || parsed.serverControl.holdBackUs != C.TIME_UNSET ||
            parsed.partTargetDurationUs != C.TIME_UNSET) parsed
        else {
            val window = parsed.durationUs / 1000
            val segment = parsed.targetDurationUs / 1000
            // Media3's ordinary default is three target durations behind the live edge.
            val safe = LiveWindowPolicy.startPosition(window, (window - 3 * segment).coerceAtLeast(0), segment)
            if (safe == null) parsed else HlsMediaPlaylist(
                parsed.playlistType, parsed.baseUri, parsed.tags, safe * 1000, false,
                parsed.startTimeUs, parsed.hasDiscontinuitySequence, parsed.discontinuitySequence,
                parsed.mediaSequence, parsed.version, parsed.targetDurationUs, parsed.partTargetDurationUs,
                parsed.hasIndependentSegments, parsed.hasEndTag, parsed.hasProgramDateTime,
                parsed.protectionSchemes, parsed.segments, parsed.trailingParts, parsed.serverControl,
                parsed.renditionReports, parsed.interstitials, parsed.lastSeenInitSegment,
            )
        }
    }
}
