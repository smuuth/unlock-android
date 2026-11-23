package dev.unlock.reader

import android.graphics.BitmapFactory
import android.util.LruCache
import android.webkit.CookieManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Schlanker Bildlader ohne Drittanbieter-Bibliothek.
 * Reicht Cookies aus dem WebView-[CookieManager] und den Referer mit,
 * damit Bilder von archive.today zuverlässig geladen werden.
 */
object ArticleImageLoader {

    private const val MAX_WIDTH_PX = 1440
    private val cache = LruCache<String, ImageBitmap>(24)

    suspend fun load(url: String, referer: String?, userAgent: String?): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    instanceFollowRedirects = true
                    if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
                    if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                    val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                    if (!cookies.isNullOrBlank()) setRequestProperty("Cookie", cookies)
                }
                try {
                    if (conn.responseCode !in 200..299) return@runCatching null
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty()) return@runCatching null

                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= MAX_WIDTH_PX) sample *= 2

                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return@runCatching null
                    bmp.asImageBitmap().also { cache.put(url, it) }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
    }
}
