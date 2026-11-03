package fr.bonamy.iptv.core

import kotlin.test.*

class TvGuideTest {
    @Test fun parsesOffsetsEscapesAndSkipsInvalidPrograms() {
        val guide = TvGuide.parse("""<?xml version="1.0"?><!DOCTYPE tv SYSTEM "https://invalid/xmltv.dtd"><tv>
          <channel id="TF1.fr"><display-name>TF1</display-name></channel>
          <programme channel="TF1.fr" start="20261103180000 +0100" stop="20261103190000 +0100"><title>News &amp; weather</title><desc>Tonight</desc></programme>
          <programme channel="TF1.fr" start="bad" stop="bad"><title>Invalid</title></programme>
          <programme channel="TF1.fr" start="20261103190000 +0100" stop="20261103180000 +0100"><title>Reversed</title></programme>
        </tv>""")
        assertEquals("TF1", guide.channels.single().name)
        val program = guide.programs.single()
        assertEquals("News & weather", program.title)
        assertEquals(java.time.Instant.parse("2026-11-03T17:00:00Z").toEpochMilli(), program.start)
        assertEquals(3600000, program.end - program.start)
    }
    @Test fun rejectsInvalidXmlAndNeverExpandsEntities() {
        assertFails { TvGuide.parse("<tv><programme></tv>") }
        assertFails { TvGuide.parse("""<!DOCTYPE tv [<!ENTITY x "secret">]><tv><channel id="x"><display-name>&x;</display-name></channel></tv>""") }
    }
    @Test fun matchesStableIdsAndExplicitLegacyAliasesOnly() {
        fun channel(id: String) = Channel(id, id, "FR", emptyList(), listOf(Stream("https://example.org/live", "Live")))
        assertEquals("TFX.fr", GuideChannel("NT1.fr", "TFX").match(listOf(channel("TFX.fr")))?.id)
        assertNull(GuideChannel("unknown", "TF1").match(listOf(channel("TF1.fr"))))
        assertEquals("TF1.fr", GuideChannel("TF1.fr", "TF1").match(listOf(channel("TF1.fr")))?.id)
    }
}
