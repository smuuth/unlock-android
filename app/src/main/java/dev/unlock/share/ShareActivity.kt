package dev.unlock.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutManagerCompat.EXTRA_SHORTCUT_ID
import dev.unlock.R
import dev.unlock.shortcuts.ShortcutPublisher
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
        openFromTexts(this, texts, override)
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
        ): Boolean {
            val app = activity.unlockApp
            val settings = app.settingsRepository.current()
            val result = LinkProcessor.process(texts, settings, targetOverride)
            if (result == null) {
                Toast.makeText(activity.applicationContext, R.string.toast_no_link, Toast.LENGTH_SHORT).show()
                return false
            }
            if (!LinkOpener.open(activity, result.targetUrl, settings.useCustomTabs)) {
                Toast.makeText(activity.applicationContext, R.string.toast_no_browser, Toast.LENGTH_SHORT).show()
                return false
            }
            if (settings.historyEnabled) {
                app.appScope.launch { app.settingsRepository.addToHistory(result.cleanedUrl) }
            }
            return true
        }
    }
}
