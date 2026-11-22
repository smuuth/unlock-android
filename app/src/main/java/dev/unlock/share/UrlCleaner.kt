package dev.unlock.share

import java.net.URLDecoder

/**
 * Bereinigt URLs offline: löst bekannte Redirect-Wrapper auf, entfernt
 * Tracking-Parameter und normalisiert optional AMP-/Mobile-Varianten.
 * Reines Kotlin, ohne Android-Abhängigkeiten.
 */
object UrlCleaner {

    data class Options(
        val removeTracking: Boolean = true,
        val normalizeAmp: Boolean = false,
    )

    private val TRACKING_EXACT = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
        "li_fat_id", "mc_cid", "mc_eid", "igshid", "igsh", "ref", "ref_src", "ref_url", "referrer", "si",
        "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id", "wickedid", "rb_clickid",
        "s_cid", "cmpid", "ocid", "smid", "sr_share", "share_id", "spm", "trk", "trkcampaign", "at_medium",
        "at_campaign", "ito", "xtor", "__twitter_impression", "_ga", "_gl",
    )
    private val TRACKING_PREFIXES = listOf("utm_", "pk_", "mtm_", "ga_", "hsa_", "at_custom")

    /** Hosts, die nur auf einen Ziel-Parameter weiterleiten: Host-Regex → Pfad → Parametername(n). */
    private data class Redirector(val host: Regex, val path: String, val params: List<String>)

    private val REDIRECTORS = listOf(
        Redirector(Regex("""(www\.)?google\.[a-z]{2,3}(\.[a-z]{2})?"""), "/url", listOf("q", "url")),
        Redirector(Regex("""(l|lm|m|www)\.facebook\.com"""), "/l.php", listOf("u")),
        Redirector(Regex("""l\.instagram\.com"""), "/", listOf("u")),
        Redirector(Regex("""(www\.|m\.)?youtube\.com"""), "/redirect", listOf("q")),
        Redirector(Regex("""out\.reddit\.com"""), "*", listOf("url")),
        Redirector(Regex("""(www\.)?linkedin\.com"""), "/safety/go", listOf("url")),
        Redirector(Regex("""slack-redir\.net"""), "/link", listOf("url")),
        Redirector(Regex("""(www\.)?duckduckgo\.com"""), "/l/", listOf("uddg")),
        Redirector(Regex("""steamcommunity\.com"""), "/linkfilter/", listOf("url", "u")),
    )

    private val AMP_QUERY_FLAGS = setOf("amp", "outputtype", "amp_js_v", "usqp", "_js_v")

    fun clean(url: String, options: Options = Options()): String {
        var current = url.trim()
        // Verschachtelte Redirects (z. B. Google → Facebook → Ziel) begrenzt auflösen.
        repeat(5) {
            val unwrapped = unwrapRedirect(current) ?: unwrapAmpCache(current) ?: return@repeat
            current = unwrapped
        }
        val parsed = ParsedUrl.parse(current) ?: return current
        var result = parsed
        if (options.removeTracking) result = result.copy(query = filterQuery(result.query) { isTrackingParam(it) })
        if (options.normalizeAmp) result = normalizeAmp(result)
        return result.toString()
    }

    fun isTrackingParam(name: String): Boolean {
        val key = name.lowercase()
        return key in TRACKING_EXACT || TRACKING_PREFIXES.any { key.startsWith(it) }
    }

    private fun unwrapRedirect(url: String): String? {
        val p = ParsedUrl.parse(url) ?: return null
        val host = p.host.lowercase()
        val redirector = REDIRECTORS.firstOrNull { r ->
            r.host.matches(host) && (r.path == "*" || p.path == r.path || p.path.startsWith(r.path) && r.path.endsWith("/"))
        } ?: return null
        val params = queryParams(p.query)
        val target = redirector.params.firstNotNullOfOrNull { name -> params.firstOrNull { it.first == name }?.second }
            ?: return null
        val decoded = decode(target)
        return decoded.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    /** `google.com/amp/s/example.com/…` bzw. `*.cdn.ampproject.org/c/s/example.com/…` → Original. */
    private fun unwrapAmpCache(url: String): String? {
        val p = ParsedUrl.parse(url) ?: return null
        val host = p.host.lowercase()
        val rest = when {
            Regex("""(www\.)?google\.[a-z.]+""").matches(host) && p.path.startsWith("/amp/") -> p.path.removePrefix("/amp/")
            host.endsWith(".cdn.ampproject.org") && p.path.matches(Regex("""/[a-z]+/.*""")) -> p.path.substringAfter('/').substringAfter('/')
            else -> return null
        }
        val (scheme, hostAndPath) = when {
            rest.startsWith("s/") -> "https" to rest.removePrefix("s/")
            rest.startsWith("i/s/") -> "https" to rest.removePrefix("i/s/")
            rest.startsWith("i/") -> "http" to rest.removePrefix("i/")
            else -> "http" to rest
        }
        if (hostAndPath.isBlank() || '.' !in hostAndPath.substringBefore('/')) return null
        return "$scheme://$hostAndPath" + (p.query?.let { "?$it" } ?: "")
    }

    private fun normalizeAmp(p: ParsedUrl): ParsedUrl {
        var host = p.host
        val labels = host.split('.')
        if (labels.size > 2 && labels.first().lowercase() in setOf("amp", "m", "mobile")) {
            host = labels.drop(1).joinToString(".")
        }
        var path = p.path
            .replace(Regex("""(?i)/amp(?=/|$)"""), "")
            .replace(Regex("""(?i)\.amp(?=\.html?$|$)"""), "")
            .replace(Regex("""(?i)/amp\.html?$"""), "/")
        if (path.isEmpty() && p.path.isNotEmpty()) path = "/"
        val query = filterQuery(p.query) { it.lowercase() in AMP_QUERY_FLAGS }
        return p.copy(host = host, path = path, query = query)
    }

    private fun filterQuery(query: String?, drop: (String) -> Boolean): String? {
        if (query == null) return null
        val kept = query.split('&').filter { part ->
            part.isNotEmpty() && !drop(decode(part.substringBefore('=')))
        }
        return kept.joinToString("&").ifEmpty { null }
    }

    private fun queryParams(query: String?): List<Pair<String, String>> =
        query.orEmpty().split('&').filter { it.isNotEmpty() }.map {
            decode(it.substringBefore('=')) to it.substringAfter('=', "")
        }

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            value
        }

    /** Minimaler, toleranter URL-Parser – java.net.URI scheitert an vielen „echten“ URLs. */
    internal data class ParsedUrl(
        val scheme: String,
        val userInfo: String?,
        val host: String,
        val port: String?,
        val path: String,
        val query: String?,
        val fragment: String?,
    ) {
        override fun toString(): String = buildString {
            append(scheme).append("://")
            userInfo?.let { append(it).append('@') }
            append(host)
            port?.let { append(':').append(it) }
            append(path)
            query?.let { append('?').append(it) }
            fragment?.let { append('#').append(it) }
        }

        companion object {
            private val PATTERN = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#(.*))?$""")

            fun parse(url: String): ParsedUrl? {
                val m = PATTERN.matchEntire(url) ?: return null
                val authority = m.groupValues[2]
                val userInfo = authority.substringBefore('@', "").ifEmpty { null }
                val hostPort = authority.substringAfter('@')
                val portMatch = Regex(""":(\d*)$""").find(hostPort)
                val host = if (portMatch != null) hostPort.substring(0, portMatch.range.first) else hostPort
                if (host.isEmpty()) return null
                return ParsedUrl(
                    scheme = m.groupValues[1].lowercase(),
                    userInfo = userInfo,
                    host = host,
                    port = portMatch?.groupValues?.get(1),
                    path = m.groupValues[3],
                    query = m.groups[4]?.value,
                    fragment = m.groups[5]?.value,
                )
            }
        }
    }
}
