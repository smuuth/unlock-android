package dev.unlock.share

/**
 * Findet die erste http(s)-URL in einem geteilten Text („Artikeltitel https://…“).
 * Reines Kotlin, ohne Android-Abhängigkeiten.
 */
object UrlExtractor {

    private val HTTP_URL = Regex("""(?i)\bhttps?://[^\s<>"'`«»“”„‘’ ]+""")
    private val WWW_URL = Regex("""(?i)(?<![\w@./-])www\.[a-z0-9-]+(\.[a-z0-9-]+)+[^\s<>"'`«»“”„‘’ ]*""")

    private const val TRAILING_PUNCTUATION = ".,;:!?…)]}>*_~'\""
    private val BRACKETS = mapOf(')' to '(', ']' to '[', '}' to '{')

    /** Alle URLs in Reihenfolge ihres Auftretens. */
    fun extractAll(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val http = HTTP_URL.findAll(text).mapNotNull { m -> trim(m.value)?.let { m.range.first to it } }
        val www = WWW_URL.findAll(text).mapNotNull { m -> trim(m.value)?.let { m.range.first to "https://$it" } }
        return (http + www).sortedBy { it.first }.map { it.second }.toList()
    }

    /** Erste URL oder `null`. Nackte `www.`-Adressen werden als https interpretiert. */
    fun extract(text: String?): String? = extractAll(text).firstOrNull()

    /** Entfernt Satzzeichen am Ende, behält aber ausgeglichene Klammern (z. B. Wikipedia-Links). */
    internal fun trim(raw: String): String? {
        var url = raw
        while (url.isNotEmpty()) {
            val last = url.last()
            if (last !in TRAILING_PUNCTUATION) break
            val open = BRACKETS[last]
            if (open != null && url.count { it == open } >= url.count { it == last }) break
            url = url.dropLast(1)
        }
        val host = url.substringAfter("://", url).substringBefore('/').substringBefore('?').substringBefore('#')
        return if (host.isBlank() || !host.any { it.isLetterOrDigit() }) null else url
    }
}
