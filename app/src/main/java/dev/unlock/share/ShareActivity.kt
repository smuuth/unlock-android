package dev.unlock.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutManagerCompat.EXTRA_SHORTCUT_ID
import dev.unlock.R
import dev.unlock.shortcuts.ShortcutPublisher
import dev.unlock.archive.ArchiveUrls
import dev.unlock.stats.StatsMath
import dev.unlock.stats.UnlockEvent
import dev.unlock.unlockApp
import kotlinx.coroutines.launch

/**
 * Unsichtbarer Einstiegspunkt aus dem Teilen-Menü und dem Textauswahlmenü.
 *
 * Bewusst eine schlanke Plattform-[Activity] (kein Compose, kein AppCompat) mit
 * `Theme.NoDisplay`: Intent auswerten, Ziel starten, sofort `finish()`.
 */
class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent?) {
        val texts = extractTexts(intent ?: return)
        val override = ShortcutPublisher.targetForShortcut(intent.getStringExtra(EXTRA_SHORTCUT_ID))
        val source = if (intent.action == Intent.ACTION_PROCESS_TEXT) "process_text" else "share"
        openFromTexts(this, texts, override, source)
        intent.getStringExtra(EXTRA_SHORTCUT_ID)?.let { ShortcutManagerCompat.reportShortcutUsed(this, it) }
    }

    companion object {
        fun extractTexts(intent: Intent): List<String?> = when (intent.action) {
            Intent.ACTION_SEND -> listOf(
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
            )
            Intent.ACTION_PROCESS_TEXT -> listOf(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString())
            else -> emptyList()
        }

        /** Gemeinsamer Pfad für Share, Zwischenablage und Testfeld. */
        fun openFromTexts(
            activity: Activity,
            texts: List<String?>,
            targetOverride: dev.unlock.archive.ArchiveTarget? = null,
            source: String = "share",
            resolveShortLinks: Boolean = true,
        ): Boolean {
            if (resolveShortLinks) {
                // Google-Kurzlinks (Discover/Google-App) erst im Hintergrund auflösen.
                val shortLink = texts.firstNotNullOfOrNull { UrlExtractor.extract(it) }
                    ?.takeIf { ShortLinkResolver.isShortLink(it) }
                if (shortLink != null) {
                    activity.startActivity(ShortLinkActivity.createIntent(activity, shortLink, targetOverride, source))
                    return true
                }
            }
            val app = activity.unlockApp
            val settings = app.settingsRepository.current()
            val result = LinkProcessor.process(texts, settings, targetOverride)
            if (result == null) {
                Toast.makeText(activity.applicationContext, R.string.toast_no_link, Toast.LENGTH_SHORT).show()
                return false
            }
            if (!LinkOpener.open(activity, result.targetUrl, result.cleanedUrl, settings.domain, settings.openMode)) {
                Toast.makeText(activity.applicationContext, R.string.toast_no_browser, Toast.LENGTH_SHORT).show()
                return false
            }
            val effectiveTarget = targetOverride ?: settings.target
            app.telemetry.track(
                "Unlock.Article.opened",
                mapOf(
                    "openMode" to settings.openMode.id,
                    "service" to effectiveTarget.service.name.lowercase(),
                    "target" to effectiveTarget.id,
                    "source" to source,
                ),
            )
            app.appScope.launch {
                app.statsRepository.record(
                    UnlockEvent(
                        source = source,
                        service = effectiveTarget.service.name.lowercase(),
                        host = result.cleanedUrl.takeUnless(ArchiveUrls::isArchiveUrl)?.let(StatsMath::hostOf),
                        trackersRemoved = StatsMath.removedParams(result.originalUrl, result.cleanedUrl),
                    ),
                )
            }
            if (settings.historyEnabled) {
                app.appScope.launch { app.settingsRepository.addToHistory(result.cleanedUrl) }
            }
            return true
        }
    }
}
