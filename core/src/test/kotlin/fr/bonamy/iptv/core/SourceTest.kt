package fr.bonamy.iptv.core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.*

class SourceTest {
    @Test fun `source HTTP failures never become catalogs`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("error"))
            assertFailsWith<java.io.IOException> { SourceClient().fetch(server.url("/index.m3u").toString()) }
        }
    }
    @Test fun `bounded recovery stops and explicit retry resets it`() {
        val recovery = PlaybackRecovery()
        assertEquals(listOf(1000L, 2000L, 4000L, null), List(4) { recovery.nextDelayMs() })
        recovery.reset()
        assertEquals(1000L, recovery.nextDelayMs())
    }
}
