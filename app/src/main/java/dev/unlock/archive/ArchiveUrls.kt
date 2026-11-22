package dev.unlock.archive

import java.net.URLDecoder

/**
 * Erkennt URLs, die bereits auf einen Archivdienst zeigen – z. B. wenn man aus
 * dem Custom Tab erneut teilt. So entsteht kein „Archiv vom Archiv“.
 */
object ArchiveUrls {

    private val URL = Regex("""^https?://([^/?#]+)/?(.*)$""", RegexOption.IGNORE_CASE)
    private val WAYBACK_TIMESTAMP = Regex("""^\d{1,14}[a-z_]*\*?$""")
    private const val WAYBACK_HOST = "web.archive.org"
    private val ARCHIVE_TODAY_HOSTS = ArchiveDomain.entries.map { it.host }.toSet()

    fun isArchiveUrl(url: String): Boolean = host(url)?.let { it in ARCHIVE_TODAY_HOSTS || it == WAYBACK_HOST } ?: false

    /** Original-URL aus einer Archiv-URL, oder `null` (kein Archiv bzw. Snapshot-ID ohne Original). */
    fun unwrap(url: String): String? {
        val m = URL.matchEntire(url) ?: return null
        val host = m.groupValues[1].lowercase().removePrefix("www.")
        var rest = m.groupValues[2]
        when (host) {
            in ARCHIVE_TODAY_HOSTS -> {
                if (rest.startsWith("submit", ignoreCase = true)) {
                    val param = rest.substringAfter('?', "").split('&').firstOrNull { it.startsWith("url=") }
                    return param?.let { normalize(decode(it.removePrefix("url="))) }
                }
                listOf("newest/", "oldest/", "timegate/", "timemap/").firstOrNull { rest.startsWith(it) }
                    ?.let { rest = rest.removePrefix(it) }
            }
            WAYBACK_HOST -> {
                rest = rest.removePrefix("web/")
                val first = rest.substringBefore('/')
                if (WAYBACK_TIMESTAMP.matches(first)) rest = rest.substringAfter('/', "")
            }
            else -> return null
        }
        return normalize(rest)
    }

    private fun normalize(inner: String): String? {
        // Browser/Server kollabieren gern "https://" zu "https:/".
        val fixed = Regex("""^(https?):/+""", RegexOption.IGNORE_CASE).replace(inner) { "${it.groupValues[1].lowercase()}://" }
        return fixed.takeIf { Regex("""^https?://[^/?#\s]+""").containsMatchIn(it) }
    }

    private fun host(url: String) = URL.matchEntire(url)?.groupValues?.get(1)?.lowercase()?.removePrefix("www.")

    private fun decode(s: String) = try {
        URLDecoder.decode(s, Charsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        s
    }
}
