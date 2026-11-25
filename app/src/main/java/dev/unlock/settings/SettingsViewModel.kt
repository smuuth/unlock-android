package dev.unlock.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.unlock.UnlockApp
import dev.unlock.analytics.TelemetryClient
import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveTarget
import dev.unlock.stats.StatsRepository
import dev.unlock.stats.UnlockStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val telemetry: TelemetryClient,
) : ViewModel() {

    /** `null` solange DataStore noch nicht gelesen wurde (→ LoadingIndicator). */
    val settings: StateFlow<Settings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<HistoryEntry>> =
        repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Für die Statistik-Vorschau auf der Startseite. */
    val stats: StateFlow<UnlockStats?> =
        statsRepository.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTarget(value: ArchiveTarget) {
        update { repo.setTarget(value) }
        telemetry.track("Unlock.Settings.changed", mapOf("setting" to "target", "value" to value.id))
    }

    fun setDomain(value: ArchiveDomain) {
        update { repo.setDomain(value) }
        telemetry.track("Unlock.Settings.changed", mapOf("setting" to "domain", "value" to value.host))
    }

    fun setCleanUrls(value: Boolean) {
        update { repo.setCleanUrls(value) }
        telemetry.track("Unlock.Settings.changed", mapOf("setting" to "cleanUrls", "value" to value.toString()))
    }

    fun setNormalizeAmp(value: Boolean) {
        update { repo.setNormalizeAmp(value) }
        telemetry.track("Unlock.Settings.changed", mapOf("setting" to "normalizeAmp", "value" to value.toString()))
    }

    fun setOpenMode(value: OpenMode) {
        update { repo.setOpenMode(value) }
        telemetry.track("Unlock.Settings.changed", mapOf("setting" to "openMode", "value" to value.id))
    }

    fun setHistoryEnabled(value: Boolean) {
        update { repo.setHistoryEnabled(value) }
        telemetry.track("Unlock.Settings.changed", mapOf("setting" to "historyEnabled", "value" to value.toString()))
    }

    fun setAnalyticsEnabled(value: Boolean) = update {
        repo.setAnalyticsEnabled(value)
        if (!value) telemetry.clearLocalState()
    }

    fun setStatsEnabled(value: Boolean) {
        update { statsRepository.setEnabled(value) }
        telemetry.track("Unlock.Settings.changed", mapOf("setting" to "statsEnabled", "value" to value.toString()))
    }

    fun clearHistory() = update { repo.clearHistory() }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UnlockApp
                SettingsViewModel(app.settingsRepository, app.statsRepository, app.telemetry)
            }
        }
    }
}
