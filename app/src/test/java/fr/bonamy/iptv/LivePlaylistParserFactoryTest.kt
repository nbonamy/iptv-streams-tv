package fr.bonamy.iptv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LivePlaylistParserFactoryTest {
    private fun parse(extra: String = "", count: Int = 4): HlsMediaPlaylist {
        val text = "#EXTM3U\n#EXT-X-TARGETDURATION:3\n#EXT-X-MEDIA-SEQUENCE:42\n$extra\n" +
            (0 until count).joinToString("") { "#EXTINF:2.0,\n$it.ts\n" }
        return LivePlaylistParserFactory().createPlaylistParser().parse(Uri.parse("https://example.com/live.m3u8"), text.byteInputStream()) as HlsMediaPlaylist
    }
    @Test fun `short live windows get a safe default while sequence and segments are preserved`() {
        val playlist = parse()
        assertEquals(4000000L, playlist.startOffsetUs)
        assertEquals(42L, playlist.mediaSequence)
        assertEquals(listOf("0.ts", "1.ts", "2.ts", "3.ts"), playlist.segments.map { it.url })
        assertFalse(playlist.preciseStart)
    }
    @Test fun `provider offsets holdback VOD event and long windows are preserved`() {
        assertEquals(1000000L, parse("#EXT-X-START:TIME-OFFSET=1").startOffsetUs)
        for (tag in listOf("#EXT-X-ENDLIST", "#EXT-X-PLAYLIST-TYPE:EVENT", "#EXT-X-SERVER-CONTROL:HOLD-BACK=6"))
            assertEquals(tag, C.TIME_UNSET, parse(tag).startOffsetUs)
        assertEquals(C.TIME_UNSET, parse(count = 20).startOffsetUs)
    }
}
