package dev.unlock

import android.app.Application
import androidx.core.content.pm.ShortcutManagerCompat
import dev.unlock.analytics.TelemetryClient
import dev.unlock.settings.SettingsRepository
import dev.unlock.stats.StatsRepository
import dev.unlock.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UnlockApp : Application() {

    /** Prozessweiter Scope für kurze Schreibzugriffe (z. B. Verlauf) – keine Hintergrunddienste. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsRepository by lazy { SettingsRepository(this) }

    /** Lokale Statistik – bleibt ausschließlich auf dem Gerät. */
    val statsRepository by lazy { StatsRepository(this) }

    val telemetry by lazy { TelemetryClient(settingsRepository, appScope) }

    /** Selbst-Update über GitHub Releases. */
    val updates by lazy { UpdateManager(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        settingsRepository.warmUp(appScope)
        // Startsignal (höchstens einmal pro Prozess; respektiert das Opt-out). Im Hintergrund, da das
        // erste Lesen der Einstellungen sonst den Main-Thread blockieren könnte.
        appScope.launch { telemetry.trackLaunch() }
        // Frühere Versionen haben eigene Direct-Share-Ziele pro Archivdienst angelegt. Heute gibt es
        // nur noch einen Eintrag im Teilen-Menü → alte (auch vom System gecachte) Shortcuts entfernen.
        appScope.launch {
            runCatching {
                ShortcutManagerCompat.removeAllDynamicShortcuts(this@UnlockApp)
                ShortcutManagerCompat.removeLongLivedShortcuts(this@UnlockApp, LEGACY_SHARE_SHORTCUTS)
            }
        }
    }

    private companion object {
        val LEGACY_SHARE_SHORTCUTS = listOf("target_newest", "target_submit", "target_all", "target_wayback")
    }
}

val android.content.Context.unlockApp: UnlockApp get() = applicationContext as UnlockApp
