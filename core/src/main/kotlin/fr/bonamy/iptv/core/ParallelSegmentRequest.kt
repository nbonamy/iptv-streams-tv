package fr.bonamy.iptv.core

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Parallel immutable HLS segment ranges, with a process-wide memory and worker limit. */
class ParallelSegmentRequest {
    private val cancelled = AtomicBoolean()
    private val aborted = AtomicBoolean()
    private val calls = ConcurrentHashMap.newKeySet<Call>()

    class Result internal constructor(val bytes: ByteArray, val uri: String, val headers: Map<String, List<String>>, private val release: () -> Unit) : Closeable {
        private val closed = AtomicBoolean()
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }

    private class Lease(private val size: Int) {
        private val owners = AtomicInteger(1)
        fun retain() { owners.incrementAndGet() }
        fun release() { if (owners.decrementAndGet() == 0) memory.release(size) }
    }

    /** Null means the origin cannot safely use ranges; the caller should stream normally. */
    fun fetch(uri: String, headers: Map<String, String>, position: Long = 0, length: Long = -1): Result? {
        checkActive()
        if (position < 0 || length < -1) return null
        var lease: Lease? = null
        var handedOff = false
        try {
            val probe = request(uri, headers, position, position)
            val call = client.newCall(probe)
            calls.add(call)
            val metadata = try {
                checkActive()
                call.execute().use { response ->
                    if (response.code != 206 || response.header("Content-Encoding", "identity") != "identity") return null
                    val range = parseRange(response.header("Content-Range")) ?: return null
                    if (range.first != position || range.second != position) return null
                    Triple(range.third, response.request.url.toString(), response.headers.toMultimap())
                }
            } finally { calls.remove(call) }
            val available = metadata.first - position
            val size = if (length < 0) available else length
            if (size !in MIN_BYTES..MAX_BYTES || size > available || !memory.tryAcquire(size.toInt())) return null
            val allocation = Lease(size.toInt()).also { lease = it }
            val bytes = ByteArray(size.toInt())
            val validator = header(metadata.third, "ETag")?.takeUnless { it.startsWith("W/") }
                ?: header(metadata.third, "Last-Modified")
            val requestHeaders = if (validator == null) headers else headers + ("If-Range" to validator)
            val jobs = (0 until PARTS).map { part ->
                val from = bytes.size * part / PARTS
                val end = bytes.size * (part + 1) / PARTS
                allocation.retain()
                try {
                    workers.submit {
                        var active: Call? = null
                        try {
                            checkActive()
                            active = client.newCall(request(uri, requestHeaders, position + from, position + end - 1))
                            calls.add(active)
                            checkActive()
                            active.execute().use { response ->
                                val range = parseRange(response.header("Content-Range"))
                                if (response.code != 206 || response.header("Content-Encoding", "identity") != "identity" ||
                                    range != Triple(position + from, position + end - 1, metadata.first)) throw IOException("Range response changed")
                                if (validator != null) {
                                    val actual = response.header(if (header(metadata.third, "ETag") == validator) "ETag" else "Last-Modified")
                                    if (actual != validator) throw IOException("Segment changed")
                                }
                                val input = response.body?.byteStream() ?: throw IOException("Empty range response")
                                var offset = from
                                while (offset < end) {
                                    checkActive()
                                    val read = input.read(bytes, offset, end - offset)
                                    if (read < 0) throw IOException("Truncated range response")
                                    offset += read
                                }
                                if (input.read() != -1) throw IOException("Oversized range response")
                            }
                        } catch (error: Exception) {
                            abort()
                            throw error
                        } finally {
                            active?.let(calls::remove)
                            allocation.release()
                        }
                    }
                } catch (error: RuntimeException) { allocation.release(); throw error }
            }
            for (job in jobs) job.get()
            checkActive()
            val resultHeaders = metadata.third.filterKeys { !it.equals("Content-Range", true) && !it.equals("Content-Length", true) } +
                ("Content-Length" to listOf(size.toString()))
            handedOff = true
            return Result(bytes, metadata.second, resultHeaders, allocation::release)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            cancel()
            throw InterruptedIOException("Segment request cancelled")
        } catch (_: ExecutionException) {
            abort()
            if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedIOException("Segment request cancelled")
            return null
        } catch (_: IOException) {
            abort()
            if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedIOException("Segment request cancelled")
            return null
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            abort()
            return null
        } finally {
            if (!handedOff) { abort(); lease?.release() }
        }
    }

    fun cancel() { cancelled.set(true); abort() }
    private fun abort() { aborted.set(true); calls.forEach(Call::cancel) }
    private fun checkActive() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedIOException("Segment request cancelled")
        if (aborted.get()) throw IOException("Segment request aborted")
    }

    companion object {
        private const val PARTS = 4
        private const val MIN_BYTES = 256L * 1024
        private const val MAX_BYTES = 8L * 1024 * 1024
        private val memory = Semaphore(16 * 1024 * 1024)
        private val workers = ThreadPoolExecutor(8, 8, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(32),
            { task -> Thread(task, "iptv-segment").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
        private fun request(uri: String, headers: Map<String, String>, from: Long, to: Long): Request = Request.Builder().url(uri).apply {
            headers.forEach { (key, value) -> header(key, value) }
            header("Range", "bytes=$from-$to"); header("Accept-Encoding", "identity")
        }.build()
        private fun parseRange(value: String?): Triple<Long, Long, Long>? = value?.let { text ->
            Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(text)?.groupValues?.drop(1)?.map { it.toLongOrNull() ?: return null }
                ?.let { Triple(it[0], it[1], it[2]) }
        }
        private fun header(headers: Map<String, List<String>>, key: String) = headers.entries.firstOrNull { it.key.equals(key, true) }?.value?.firstOrNull()
    }
}
