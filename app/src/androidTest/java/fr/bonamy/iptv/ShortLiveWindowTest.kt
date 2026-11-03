package fr.bonamy.iptv

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.bonamy.iptv.core.Channel
import fr.bonamy.iptv.core.Stream
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** An original synthetic live stream with a short sliding window and one delayed request. */
@RunWith(AndroidJUnit4::class)
class ShortLiveWindowTest {
    @Test fun startsWithHeadroomInsteadOfAnExpiringSegment() {
        Origin().use { origin ->
            val channel = Channel("fixture", "Local live window", null, emptyList(), listOf(Stream(origin.url, "Main")))
            StreamStabilityTest().probe(channel, durationOverrideMs = 25000, startupLimitMs = 6500)
        }
    }

    private class Origin : Closeable {
        private val assets = InstrumentationRegistry.getInstrumentation().context.assets
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val firstMedia = AtomicBoolean(true)
        @Volatile private var started = 0L
        val url = "http://127.0.0.1:${server.localPort}/index.m3u8"
        init {
            thread(isDaemon = true, name = "live-window-origin") {
                while (!server.isClosed) {
                    val client = try { server.accept() } catch (_: java.io.IOException) { break }
                    thread(isDaemon = true) {
                        client.use { socket ->
                            socket.soTimeout = 5000
                            val input = socket.getInputStream().bufferedReader()
                            val path = input.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                            while (!input.readLine().isNullOrEmpty()) { /* Headers are irrelevant to this fixture. */ }
                            var status = 200
                            val body: ByteArray
                            val type: String
                            if (path == "/index.m3u8") {
                                synchronized(this) { if (started == 0L) started = SystemClock.elapsedRealtime() }
                                val base = ((SystemClock.elapsedRealtime() - started) / 2000).toInt()
                                body = buildString {
                                    append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:3\n#EXT-X-MEDIA-SEQUENCE:$base\n")
                                    for (index in base..base + 3) append("#EXTINF:2.0,\n/${index.toString().padStart(2, '0')}.ts\n")
                                }.toByteArray()
                                type = "application/vnd.apple.mpegurl"
                            } else {
                                if (firstMedia.compareAndSet(true, false)) SystemClock.sleep(2500)
                                val index = path.removePrefix("/").substringBefore('.').toIntOrNull() ?: -1
                                val base = ((SystemClock.elapsedRealtime() - started) / 2000).toInt()
                                if (index < base || index > base + 3) {
                                    status = 404; body = ByteArray(0)
                                } else body = assets.open("live-window/${index.toString().padStart(2, '0')}.ts").use { it.readBytes() }
                                type = "video/mp2t"
                            }
                            val output = socket.getOutputStream()
                            output.write("HTTP/1.1 $status ${if (status == 200) "OK" else "Not Found"}\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(body); output.flush()
                        }
                    }
                }
            }
        }
        override fun close() { server.close() }
    }
}
