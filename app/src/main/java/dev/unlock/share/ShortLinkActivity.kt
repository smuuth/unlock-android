package dev.unlock.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.unlock.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparentes Zwischenfenster für Google-Kurzlinks: löst den Link im Hintergrund
 * auf und öffnet dann das Archiv. Nötig, weil [ShareActivity] (`Theme.NoDisplay`)
 * sofort in `onCreate` beendet werden muss und nicht auf das Netzwerk warten darf.
 */
class ShortLinkActivity : Activity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null || savedInstanceState != null) {
            finishQuietly()
            return
        }
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "share"
        scope.launch {
            val resolved = withContext(Dispatchers.IO) { ShortLinkResolver.resolve(url) }
            if (resolved == null) {
                Toast.makeText(applicationContext, R.string.toast_short_link_failed, Toast.LENGTH_SHORT).show()
            }
            ShareActivity.openFromTexts(this@ShortLinkActivity, listOf(resolved ?: url), source, resolveShortLinks = false)
            finishQuietly()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_URL = "dev.unlock.extra.SHORT_URL"
        private const val EXTRA_SOURCE = "dev.unlock.extra.SOURCE"

        fun createIntent(context: Context, url: String, source: String): Intent =
            Intent(context, ShortLinkActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_SOURCE, source)
    }
}
