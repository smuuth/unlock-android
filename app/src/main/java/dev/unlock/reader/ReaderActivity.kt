package dev.unlock.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.unlock.archive.ArchiveDomain
import dev.unlock.settings.Settings
import dev.unlock.share.LinkOpener
import dev.unlock.ui.theme.UnlockTheme
import dev.unlock.unlockApp
import kotlinx.coroutines.launch

/**
 * Integrierte Leseansicht für archivierte Artikel.
 * Lädt die Archivseite in einer WebView (inkl. Cookie-Persistenz für Captchas)
 * und extrahiert den Artikel automatisch in eine native Material-3-Expressive-Ansicht.
 */
class ReaderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val archiveUrl = intent.getStringExtra(EXTRA_ARCHIVE_URL)
        val originalUrl = intent.getStringExtra(EXTRA_ORIGINAL_URL)
        val domain = ArchiveDomain.fromHost(intent.getStringExtra(EXTRA_DOMAIN)) ?: ArchiveDomain.DEFAULT

        if (archiveUrl.isNullOrBlank() || originalUrl.isNullOrBlank()) {
            finish()
            return
        }

        val repo = unlockApp.settingsRepository
        val initialSettings = repo.current()

        setContent {
            val settings by repo.settings.collectAsState(initial = initialSettings)
            UnlockTheme {
                ReaderScreen(
                    initialArchiveUrl = archiveUrl,
                    originalUrl = originalUrl,
                    initialDomain = domain,
                    settings = settings,
                    onUpdateTheme = { theme ->
                        unlockApp.appScope.launch { repo.setReaderTheme(theme) }
                        unlockApp.telemetry.track("Unlock.Reader.appearanceChanged", mapOf("theme" to theme.id))
                    },
                    onUpdateFont = { font ->
                        unlockApp.appScope.launch { repo.setReaderFont(font) }
                        unlockApp.telemetry.track("Unlock.Reader.appearanceChanged", mapOf("font" to font.id))
                    },
                    onUpdateFontScale = { scale -> unlockApp.appScope.launch { repo.setReaderFontScale(scale) } },
                    onTrackEvent = { type, params -> unlockApp.telemetry.track(type, params) },
                    onOpenInBrowser = { url -> LinkOpener.openExternal(this, url, useCustomTabs = false) },
                    onShareUrl = { url ->
                        startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
                                null,
                            ),
                        )
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ARCHIVE_URL = "dev.unlock.extra.ARCHIVE_URL"
        const val EXTRA_ORIGINAL_URL = "dev.unlock.extra.ORIGINAL_URL"
        const val EXTRA_DOMAIN = "dev.unlock.extra.DOMAIN"

        fun createIntent(
            context: Context,
            archiveUrl: String,
            originalUrl: String,
            domain: ArchiveDomain,
        ): Intent = Intent(context, ReaderActivity::class.java)
            .putExtra(EXTRA_ARCHIVE_URL, archiveUrl)
            .putExtra(EXTRA_ORIGINAL_URL, originalUrl)
            .putExtra(EXTRA_DOMAIN, domain.host)
    }
}
