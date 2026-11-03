package fr.bonamy.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import coil.imageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IPTVApplication : Application(), ImageLoaderFactory {
    internal val libraryStore by lazy { LibraryStore(this) }
    internal val guideStore by lazy { GuideStore(this) }
    override fun onCreate() {
        super.onCreate()
        // Prepare artwork infrastructure off the UI thread before the first country opens.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { applicationContext.imageLoader } }
    }
    override fun newImageLoader() = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", "IPTV/0.1 (Android TV; fr.bonamy.iptv)").build())
                }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.08).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("channel-logos")).maxSizeBytes(25L * 1024 * 1024).build() }
        .build()
}
