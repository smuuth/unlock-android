package dev.unlock.stats

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.unlock.UnlockApp
import dev.unlock.stats.ui.StatsScreen
import dev.unlock.ui.theme.UnlockTheme
import dev.unlock.unlockApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Eigene Unterseite für die lokale Statistik. */
class StatsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UnlockTheme {
                StatsScreen(onBack = { finish() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        unlockApp.telemetry.trackNavigation("/stats")
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, StatsActivity::class.java)
    }
}

class StatsViewModel(private val repo: StatsRepository) : ViewModel() {

    /** `null`, solange DataStore noch nicht gelesen wurde. */
    val stats: StateFlow<UnlockStats?> =
        repo.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(value: Boolean) = launch { repo.setEnabled(value) }
    fun setArticlePrice(cents: Int) = launch { repo.setArticlePrice(cents) }
    fun reset() = launch { repo.reset() }
    fun seedDemo() = launch { repo.seedDemo() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StatsViewModel((this[APPLICATION_KEY] as UnlockApp).statsRepository) }
        }
    }
}
