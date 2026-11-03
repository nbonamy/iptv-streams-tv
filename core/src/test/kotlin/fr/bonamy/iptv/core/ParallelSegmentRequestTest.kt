package fr.bonamy.iptv.core

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ParallelSegmentRequestTest {
    private val bytes = ByteArray(512 * 1024) { (it * 31).toByte() }

    private fun response(request: RecordedRequest, data: ByteArray = bytes): MockResponse {
        val range = Regex("bytes=(\\d+)-(\\d+)").matchEntire(request.getHeader("Range")!!)!!
        val from = range.groupValues[1].toInt()
        val to = range.groupValues[2].toInt()
        return MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $from-$to/${data.size}")
            .setHeader("ETag", "\"segment\"").setBody(Buffer().write(data, from, to - from + 1))
    }

    @Test fun `ranges download concurrently and reproduce the requested bytes with source headers`() {
        MockWebServer().use { server ->
            val ready = CountDownLatch(4)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    assertEquals("private", request.getHeader("X-Source"))
                    assertEquals("identity", request.getHeader("Accept-Encoding"))
                    if (request.getHeader("Range") != "bytes=120-120") {
                        assertEquals("\"segment\"", request.getHeader("If-Range"))
                        ready.countDown()
                        check(ready.await(5, TimeUnit.SECONDS)) { "Ranges were serialized" }
                    }
                    return response(request)
                }
            }
            val result = assertNotNull(ParallelSegmentRequest().fetch(server.url("/segment").toString(), mapOf("X-Source" to "private"), 120, 300000))
            result.use {
                assertContentEquals(bytes.copyOfRange(120, 300120), it.bytes)
                assertEquals(server.url("/segment").toString(), it.uri)
                assertEquals(listOf("300000"), it.headers["Content-Length"])
                assertTrue(it.headers.keys.none { key -> key.equals("Content-Range", true) })
            }
            result.close() // Closing twice must not release the memory budget twice.
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun `origins without range support fall back without downloading the whole body`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            assertNull(ParallelSegmentRequest().fetch(server.url("/segment").toString(), emptyMap()))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `malformed ranges changed entities and truncated bodies never expose partial data`() {
        for (failure in listOf("range", "entity", "truncated")) MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val normal = response(request)
                    if (request.getHeader("Range") == "bytes=0-0") return normal
                    return when (failure) {
                        "range" -> normal.setHeader("Content-Range", "bytes 0-0/${bytes.size}")
                        "entity" -> normal.setHeader("ETag", "\"different\"")
                        else -> normal.setBody("short")
                    }
                }
            }
            assertNull(ParallelSegmentRequest().fetch(server.url("/segment").toString(), emptyMap()), failure)
        }
    }

    @Test fun `cancellation interrupts blocked range requests promptly`() {
        MockWebServer().use { server ->
            val ready = CountDownLatch(4)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.getHeader("Range") == "bytes=0-0") return response(request)
                    ready.countDown()
                    return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                }
            }
            val executor = Executors.newSingleThreadExecutor()
            try {
                val fetch = ParallelSegmentRequest()
                val task = executor.submit<Boolean> {
                    assertFailsWith<InterruptedIOException> { fetch.fetch(server.url("/segment").toString(), emptyMap()) }
                    true
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                fetch.cancel()
                assertTrue(task.get(3, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun `small and oversized segments fall back before allocating range workers`() {
        for (size in listOf(1000, 9 * 1024 * 1024)) MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-0/$size").setBody("x"))
            assertNull(ParallelSegmentRequest().fetch(server.url("/segment").toString(), emptyMap()))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `open results share a bounded memory budget and closing makes it reusable`() {
        MockWebServer().use { server ->
            val data = ByteArray(8 * 1024 * 1024)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = response(request, data)
            }
            val url = server.url("/large").toString()
            val first = assertNotNull(ParallelSegmentRequest().fetch(url, emptyMap()))
            try {
                val second = assertNotNull(ParallelSegmentRequest().fetch(url, emptyMap()))
                try { assertNull(ParallelSegmentRequest().fetch(url, emptyMap())) }
                finally { second.close() }
                assertNotNull(ParallelSegmentRequest().fetch(url, emptyMap())).close()
            } finally { first.close() }
        }
    }
}
