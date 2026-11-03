package fr.bonamy.iptv.core

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Source {
    const val PLAYLIST = "https://iptv-org.github.io/iptv/index.m3u"
    const val METADATA = "https://iptv-org.github.io/api/channels.json"
}

/** Shared bounded transport. The source is built in; there is no user source input. */
class SourceClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).build()
    fun fetch(url: String): String = client.newCall(Request.Builder().url(url).header("User-Agent", "IPTV/0.1").build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Source unavailable")
        val body = response.body ?: throw IOException("Empty source")
        val limit = 32L * 1024 * 1024
        if (body.contentLength() > limit) throw IOException("Source too large")
        body.byteStream().use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > limit) throw IOException("Source too large")
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }
    }
}

/** Deliberate retry of the same stream; alternatives are only chosen by the viewer. */
class PlaybackRecovery {
    var attempts = 0
        private set
    fun nextDelayMs(): Long? = if (attempts >= 3) null else (1000L shl attempts++)
    fun reset() { attempts = 0 }
}
