package dev.unlock.share

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Looper
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import dev.unlock.archive.ArchiveTarget
import dev.unlock.reader.ReaderActivity
import dev.unlock.shortcuts.ShortcutPublisher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Simuliert den Share-Intent aus einer fremden App. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareActivityTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before fun registerBrowser() {
        val browser = ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.example.browser"
                name = "Browser"
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "com.example.browser" }
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://archive.ph/")),
            browser,
        )
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName("com.example.browser", "Browser"))
    }

    private fun share(intent: Intent): Pair<ShareActivity, Intent?> {
        val controller = Robolectric.buildActivity(ShareActivity::class.java, intent).create()
        shadowOf(Looper.getMainLooper()).idle()
        val activity = controller.get()
        return activity to shadowOf(activity).nextStartedActivity
    }

    @Test fun sendTextOpensReaderAndFinishes() {
        val (activity, started) = share(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Spannender Artikel https://example.com/story?utm_source=x."),
        )
        assertTrue(activity.isFinishing)
        assertEquals(ReaderActivity::class.java.name, started!!.component?.className)
        assertEquals("https://archive.ph/newest/https://example.com/story", started.getStringExtra(ReaderActivity.EXTRA_ARCHIVE_URL))
        assertEquals("https://example.com/story", started.getStringExtra(ReaderActivity.EXTRA_ORIGINAL_URL))
    }

    @Test fun processTextIsSupported() {
        val (_, started) = share(
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "https://example.org/a"),
        )
        assertEquals("https://archive.ph/newest/https://example.org/a", started!!.getStringExtra(ReaderActivity.EXTRA_ARCHIVE_URL))
    }

    @Test fun subjectIsFallback() {
        val (_, started) = share(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Nur ein Titel")
                .putExtra(Intent.EXTRA_SUBJECT, "https://example.net/s"),
        )
        assertEquals("https://archive.ph/newest/https://example.net/s", started!!.getStringExtra(ReaderActivity.EXTRA_ARCHIVE_URL))
    }

    @Test fun directShareShortcutOverridesService() {
        val (_, started) = share(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://example.com/a")
                .putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, ShortcutPublisher.shortcutId(ArchiveTarget.WAYBACK)),
        )
        assertEquals("https://web.archive.org/web/https://example.com/a", started!!.getStringExtra(ReaderActivity.EXTRA_ARCHIVE_URL))
    }

    @Test fun noLinkShowsToastAndFinishes() {
        val (activity, started) = share(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "kein Link hier"),
        )
        assertTrue(activity.isFinishing)
        assertNull(started)
        assertEquals("No link found", ShadowToast.getTextOfLatestToast())
    }
}
