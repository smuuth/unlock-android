package dev.unlock.share

import dev.unlock.archive.ArchiveUrls
import dev.unlock.archive.TargetUrlBuilder
import dev.unlock.settings.Settings

/** Verbindet Extraktion → Bereinigung → Ziel-URL. Reines Kotlin, ohne Android. */
object LinkProcessor {

    data class Result(val originalUrl: String, val cleanedUrl: String, val targetUrl: String)

    /**
     * @param texts Kandidaten in Prioritätsreihenfolge (z. B. EXTRA_TEXT, dann EXTRA_SUBJECT).
     */
    fun process(texts: List<String?>, settings: Settings): Result? {
        val extracted = texts.firstNotNullOfOrNull { UrlExtractor.extract(it) } ?: return null
        // Bereits archivierte Links (z. B. erneut aus dem Custom Tab geteilt) nicht doppelt verpacken.
        val url = ArchiveUrls.unwrap(extracted)
            ?: if (ArchiveUrls.isArchiveUrl(extracted)) return Result(extracted, extracted, extracted) else extracted
        val cleaned = if (settings.cleanUrls) {
            UrlCleaner.clean(url, UrlCleaner.Options(removeTracking = true, normalizeAmp = settings.normalizeAmp))
        } else {
            url
        }
        return Result(extracted, cleaned, TargetUrlBuilder.build(cleaned, settings.target, settings.domain))
    }
}
