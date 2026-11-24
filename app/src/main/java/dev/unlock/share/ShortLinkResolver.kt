package dev.unlock.share

import java.net.HttpURLConnection
import java.net.URL

/**
 * Löst Google-Kurzlinks (`share.google/…`, `search.app/…`) auf, wie sie z. B.
 * Google Discover und die Google-App beim Teilen erzeugen. Archivdienste können
 * mit diesen Links nichts anfangen, das Ziel steht nur im HTTP-Redirect.
 *
 * Es werden ausschließlich Redirects *innerhalb* der Google-Kurzlink-Hosts
 * verfolgt – die Zielseite selbst wird nie kontaktiert.
 */
object ShortLinkResolver {

    private val SHORT_HOSTS = setOf("share.google", "search.app")
    private const val MAX_HOPS = 5
    private const val TIMEOUT_MS = 5_000

    fun isShortLink(url: String): Boolean {
        val p = UrlCleaner.ParsedUrl.parse(url) ?: return false
        return p.host.lowercase().removePrefix("www.") in SHORT_HOSTS && p.path.length > 1
    }

    /** Zwischenstation `https://www.google.com/share.google?q=…` gehört noch zur Auflösung. */
    private fun isResolverHop(url: String): Boolean {
        if (isShortLink(url)) return true
        val p = UrlCleaner.ParsedUrl.parse(url) ?: return false
        return Regex("""(www\.)?google\.[a-z.]+""").matches(p.host.lowercase()) && p.path == "/share.google"
    }

    /**
     * Blockierend (nur außerhalb des Main-Threads aufrufen).
     * @return die Ziel-URL oder `null`, wenn die Auflösung scheitert.
     */
    fun resolve(url: String, fetchLocation: (String) -> String? = ::fetchLocation): String? {
        var current = url
        repeat(MAX_HOPS) {
            if (!isResolverHop(current)) return current
            val next = fetchLocation(current) ?: return null
            current = URL(URL(current), next).toString()
        }
        return current.takeUnless { isResolverHop(it) }
    }

    /** Liest nur den `Location`-Header, ohne Cookies und ohne automatisches Folgen. */
    private fun fetchLocation(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Unlock")
            if (conn.responseCode in 300..399) conn.getHeaderField("Location") else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
