package fr.bonamy.iptv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ParallelMediaDataSourceTest {
    @Test fun `unsupported range streams through the regular source and reports the whole transfer`() {
        MockWebServer().use { server ->
            val bytes = ByteArray(300000) { it.toByte() }
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val source = ParallelMediaDataSource(DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("X-Source" to "kept")), mapOf("X-Source" to "kept"))
            var starts = 0; var ends = 0; var transferred = 0
            source.addTransferListener(object : TransferListener {
                override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, network: Boolean) {}
                override fun onTransferStart(source: DataSource, dataSpec: DataSpec, network: Boolean) { starts++ }
                override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, network: Boolean, bytes: Int) { transferred += bytes }
                override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, network: Boolean) { ends++ }
            })
            assertEquals(bytes.size.toLong(), source.open(DataSpec(Uri.parse(server.url("/segment").toString()))))
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = source.read(buffer, 0, buffer.size)
                if (count == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, count)
            }
            source.close(); source.close()
            assertArrayEquals(bytes, output.toByteArray())
            assertEquals(bytes.size, transferred)
            assertEquals(1, starts); assertEquals(1, ends)
            repeat(2) { assertEquals("kept", server.takeRequest().getHeader("X-Source")) }
        }
    }

    @Test fun `closing an in-flight open cancels it without launching a fallback request`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val source = ParallelMediaDataSource(DefaultHttpDataSource.Factory(), emptyMap())
            val executor = Executors.newSingleThreadExecutor()
            try {
                val task = executor.submit<Boolean> {
                    assertThrows(InterruptedIOException::class.java) { source.open(DataSpec(Uri.parse(server.url("/segment").toString()))) }
                    true
                }
                assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                source.close()
                assertTrue(task.get(3, TimeUnit.SECONDS))
                assertEquals(1, server.requestCount)
                // The same adapter can be reopened after cancellation.
                server.enqueue(MockResponse().setBody("short"))
                server.enqueue(MockResponse().setBody("short"))
                assertEquals(5L, source.open(DataSpec(Uri.parse(server.url("/next").toString()))))
                source.close()
            } finally { executor.shutdownNow() }
        }
    }
}
