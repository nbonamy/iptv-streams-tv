package fr.bonamy.iptv

import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class RemoteKeyboardTest {
    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2000; connection.readTimeout = 2000
        return try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
    }
    @Test fun qrCodeDeliversPhoneSearchAndListenerFollowsVisibility() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.search_button)).perform(click())
            var endpoint = ""
            onView(withContentDescription("Scan to search from your phone")).check { view, error ->
                if (error != null) throw error
                val bitmap = ((view as ImageView).drawable as BitmapDrawable).bitmap
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                endpoint = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
            }
            assertTrue(request(endpoint).contains("Search live channels"))
            assertTrue(request("$endpoint/search?q=France+24").contains("France 24"))
            val deadline = android.os.SystemClock.elapsedRealtime() + 30000
            var delivered = false
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity {
                    val items = (it.findViewById<RecyclerView>(R.id.channel_grid).adapter as ChannelAdapter).channels
                    delivered = items.size == 1 && items[0].id == "France24.fr"
                }
                if (delivered) break
                android.os.SystemClock.sleep(100)
            }
            assertTrue("Phone query should reach the TV catalog", delivered)
            scenario.moveToState(Lifecycle.State.CREATED)
            assertThrows(java.io.IOException::class.java) { request(endpoint) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue(request(endpoint).contains("IPTV"))
        }
    }
}
