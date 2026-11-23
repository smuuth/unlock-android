package dev.unlock.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import dev.unlock.R
import dev.unlock.archive.ArchiveDomain
import dev.unlock.reader.ReaderActivity
import dev.unlock.settings.OpenMode

/** Öffnet die Ziel-URL im integrierten Lesemodus, im Custom Tab oder im Standardbrowser. */
object LinkOpener {

    /** @return `false`, wenn überhaupt keine App die URL öffnen kann. */
    fun open(
        activity: Activity,
        targetUrl: String,
        originalUrl: String,
        domain: ArchiveDomain,
        openMode: OpenMode,
    ): Boolean = when (openMode) {
        OpenMode.READER -> {
            activity.startActivity(ReaderActivity.createIntent(activity, targetUrl, originalUrl, domain))
            true
        }
        OpenMode.CUSTOM_TAB -> openExternal(activity, targetUrl, useCustomTabs = true)
        OpenMode.BROWSER -> openExternal(activity, targetUrl, useCustomTabs = false)
    }

    /** Öffnet eine URL extern im Custom Tab oder Standardbrowser. */
    fun openExternal(activity: Activity, url: String, useCustomTabs: Boolean): Boolean {
        val uri = url.toUri()
        val provider = if (useCustomTabs) customTabsPackage(activity) else null
        if (provider != null) {
            val intent = buildCustomTab(activity)
            intent.intent.setPackage(provider)
            try {
                intent.launchUrl(activity, uri)
                return true
            } catch (_: ActivityNotFoundException) {
                // Fällt unten auf ACTION_VIEW zurück.
            }
        }
        return try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Bevorzugt den Standardbrowser, sofern er Custom Tabs unterstützt. */
    fun customTabsPackage(context: Context): String? = CustomTabsClient.getPackageName(context, null)

    /** Bindet den Custom-Tabs-Dienst vorab und wärmt den Browser auf (z. B. beim Öffnen der Einstellungen). */
    fun warmUp(context: Context) {
        val pkg = customTabsPackage(context) ?: return
        CustomTabsClient.connectAndInitialize(context.applicationContext, pkg)
    }

    private fun buildCustomTab(context: Context): CustomTabsIntent {
        val light = toolbarColors(context, dark = false)
        val dark = toolbarColors(context, dark = true)
        return CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setOpenInBrowserButtonState(CustomTabsIntent.OPEN_IN_BROWSER_STATE_ON)
            .setUrlBarHidingEnabled(true)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, light)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, dark)
            .build()
    }

    /** Toolbar in Dynamic Color (Android 12+), sonst in den Fallback-Farben der App. */
    private fun toolbarColors(context: Context, dark: Boolean): CustomTabColorSchemeParams {
        val toolbar = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_accent1_50)
        } else {
            context.getColor(if (dark) R.color.fallback_toolbar_dark else R.color.fallback_toolbar_light)
        }
        return CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbar)
            .setNavigationBarColor(toolbar)
            .build()
    }
}
