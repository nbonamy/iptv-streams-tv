package fr.bonamy.iptv.core

import kotlin.test.*

class LiveWindowPolicyTest {
    @Test fun `short HLS windows need a full segment of headroom at startup`() {
        assertEquals(11000L, LiveWindowPolicy.startPosition(22000, 0, 7000))
        assertEquals(4000L, LiveWindowPolicy.startPosition(8000, 0, 3000))
    }
    @Test fun `preserve safe provider positions and long windows`() {
        assertNull(LiveWindowPolicy.startPosition(22000, 12000, 7000))
        assertNull(LiveWindowPolicy.startPosition(3600000, 0, 6000))
        assertNull(LiveWindowPolicy.startPosition(22000, 8000, 7000))
    }
    @Test fun `unknown or insufficient windows retain default playback`() {
        assertNull(LiveWindowPolicy.startPosition(-1, 0, 7000))
        assertNull(LiveWindowPolicy.startPosition(22000, -1, 7000))
        assertNull(LiveWindowPolicy.startPosition(1000, 0, 7000))
        assertNull(LiveWindowPolicy.startPosition(22000, 0, 0))
    }
}
