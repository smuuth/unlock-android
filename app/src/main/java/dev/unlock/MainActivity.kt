package dev.unlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.unlock.settings.ui.HomeScreen
import dev.unlock.share.LinkOpener
import dev.unlock.ui.theme.UnlockTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UnlockTheme {
                HomeScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Browser im Hintergrund aufwärmen, damit „Öffnen“ auf der Startseite sofort reagiert.
        LinkOpener.warmUp(this)
    }
}
