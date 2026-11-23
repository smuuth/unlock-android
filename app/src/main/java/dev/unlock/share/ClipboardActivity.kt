package dev.unlock.share

import android.app.Activity
import android.content.ClipboardManager
import android.os.Bundle
import dev.unlock.shortcuts.ShortcutPublisher

/**
 * Ziel des Launcher-Shortcuts „Link aus Zwischenablage öffnen“.
 *
 * Seit Android 10 darf nur die App mit Fensterfokus die Zwischenablage lesen.
 * Daher ein transparentes (aber nicht `NoDisplay`) Fenster, das auf den Fokus
 * wartet, die URL weiterreicht und sich sofort schließt.
 */
class ClipboardActivity : Activity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handled = savedInstanceState?.getBoolean(KEY_HANDLED) ?: false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        val texts = (0 until (clip?.itemCount ?: 0)).map { clip!!.getItemAt(it).coerceToText(this)?.toString() }
        val override = ShortcutPublisher.targetForShortcut(intent.getStringExtra(EXTRA_TARGET))
        ShareActivity.openFromTexts(this, texts, override, source = "clipboard")
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HANDLED, handled)
    }

    companion object {
        const val EXTRA_TARGET = "dev.unlock.extra.TARGET"
        private const val KEY_HANDLED = "handled"
    }
}
