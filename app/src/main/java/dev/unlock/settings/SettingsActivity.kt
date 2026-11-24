package dev.unlock.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.unlock.settings.ui.SettingsScreen
import dev.unlock.ui.theme.UnlockTheme

/** Eigene Unterseite für alle Einstellungen – die Startseite bleibt dadurch schlank. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UnlockTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}
