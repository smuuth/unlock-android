package dev.unlock.share

import dev.unlock.archive.ArchiveTarget
import dev.unlock.archive.ArchiveUrls
import dev.unlock.archive.TargetUrlBuilder
import dev.unlock.settings.Settings

/** Verbindet Extraktion → Bereinigung → Ziel-URL. Reines Kotlin, ohne Android. */
object LinkProcessor {

    data class Result(val originalUrl: String, val cleanedUrl: String, val targetUrl: String)

    /**
     * @param texts Kandidaten in Prioritätsreihenfolge (z. B. EXTRA_TEXT, dann EXTRA_SUBJECT).
     * @param targetOverride z. B. aus einem Direct-Share-Shortcut; sonst gilt die Einstellung.
     */
    fun process(texts: List<String?>, settings: Settings, targetOverride: ArchiveTarget? = null): Result? {
        val extracted = texts.firstNotNullOfOrNull { UrlExtractor.extract(it) } ?: return null
        // Bereits archivierte Links (z. B. erneut aus dem Custom Tab geteilt) nicht doppelt verpacken.
        val url = ArchiveUrls.unwrap(extracted)
            ?: if (ArchiveUrls.isArchiveUrl(extracted)) return Result(extracted, extracted, extracted) else extracted
        val cleaned = if (settings.cleanUrls) {
            UrlCleaner.clean(url, UrlCleaner.Options(removeTracking = true, normalizeAmp = settings.normalizeAmp))
        } else {
            url
        }
        val target = targetOverride ?: settings.target
        return Result(extracted, cleaned, TargetUrlBuilder.build(cleaned, target, settings.domain))
    }
}
