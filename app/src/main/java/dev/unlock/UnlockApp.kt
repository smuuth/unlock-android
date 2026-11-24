package dev.unlock

import android.app.Application
import dev.unlock.analytics.TelemetryClient
import dev.unlock.settings.SettingsRepository
import dev.unlock.shortcuts.ShortcutPublisher
import dev.unlock.stats.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class UnlockApp : Application() {

    /** Prozessweiter Scope für kurze Schreibzugriffe (z. B. Verlauf) – keine Hintergrunddienste. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsRepository by lazy { SettingsRepository(this) }

    /** Lokale Statistik – bleibt ausschließlich auf dem Gerät. */
    val statsRepository by lazy { StatsRepository(this) }

    val telemetry by lazy { TelemetryClient(settingsRepository, appScope) }

    override fun onCreate() {
        super.onCreate()
        settingsRepository.warmUp(appScope)
        // Direct-Share-Ziele tragen die gewählte Domain im Namen → bei Änderung neu veröffentlichen.
        settingsRepository.settings
            .distinctUntilChangedBy { it.domain }
            .onEach { ShortcutPublisher.publish(this, it.domain) }
            .launchIn(appScope)
    }
}

val android.content.Context.unlockApp: UnlockApp get() = applicationContext as UnlockApp
