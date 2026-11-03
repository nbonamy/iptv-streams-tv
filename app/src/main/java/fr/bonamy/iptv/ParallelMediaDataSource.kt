package fr.bonamy.iptv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import fr.bonamy.iptv.core.ParallelSegmentRequest
import java.io.InterruptedIOException

@UnstableApi
internal class ParallelMediaDataSource(private val fallbackFactory: HttpDataSource.Factory, private val headers: Map<String, String>) : BaseDataSource(true) {
    @Volatile private var request: ParallelSegmentRequest? = null
    private var result: ParallelSegmentRequest.Result? = null
    private var fallback: HttpDataSource? = null
    private var offset = 0
    private var started = false
    private val lock = Any()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        try {
            val fetch = synchronized(lock) {
                check(request == null) { "Data source is already open" }
                ParallelSegmentRequest().also { request = it; started = true; transferStarted(dataSpec) }
            }
            val downloaded = if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET) fetch.fetch(dataSpec.uri.toString(), headers + dataSpec.httpRequestHeaders, dataSpec.position, dataSpec.length) else null
            synchronized(lock) {
                if (request !== fetch) { downloaded?.close(); throw InterruptedIOException("Segment request cancelled") }
                result = downloaded
                if (downloaded == null) fallback = fallbackFactory.createDataSource()
            }
            offset = 0
            return downloaded?.bytes?.size?.toLong() ?: (fallback ?: throw InterruptedIOException("Segment request cancelled")).open(dataSpec)
        } catch (error: Exception) { close(); throw error }
    }
    override fun read(buffer: ByteArray, bufferOffset: Int, length: Int): Int {
        if (length == 0) return 0
        val bytes = result?.bytes
        val read = if (bytes != null) {
            if (offset == bytes.size) return C.RESULT_END_OF_INPUT
            minOf(length, bytes.size - offset).also { count -> bytes.copyInto(buffer, bufferOffset, offset, offset + count); offset += count }
        } else fallback?.read(buffer, bufferOffset, length) ?: C.RESULT_END_OF_INPUT
        if (read > 0) bytesTransferred(read)
        return read
    }
    override fun getUri(): Uri? = result?.uri?.let(Uri::parse) ?: fallback?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = result?.headers ?: fallback?.responseHeaders ?: emptyMap()
    override fun close() {
        synchronized(lock) {
            request?.cancel(); request = null
            result?.close(); result = null
            try { fallback?.close() } finally {
                fallback = null
                if (started) { started = false; transferEnded() }
            }
        }
    }
}
