package dev.unlock.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.unlock.UnlockApp
import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveTarget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    /** `null` solange DataStore noch nicht gelesen wurde (→ LoadingIndicator). */
    val settings: StateFlow<Settings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<HistoryEntry>> =
        repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTarget(value: ArchiveTarget) = update { repo.setTarget(value) }
    fun setDomain(value: ArchiveDomain) = update { repo.setDomain(value) }
    fun setCleanUrls(value: Boolean) = update { repo.setCleanUrls(value) }
    fun setNormalizeAmp(value: Boolean) = update { repo.setNormalizeAmp(value) }
    fun setOpenMode(value: OpenMode) = update { repo.setOpenMode(value) }
    fun setHistoryEnabled(value: Boolean) = update { repo.setHistoryEnabled(value) }
    fun clearHistory() = update { repo.clearHistory() }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel((this[APPLICATION_KEY] as UnlockApp).settingsRepository) }
        }
    }
}
