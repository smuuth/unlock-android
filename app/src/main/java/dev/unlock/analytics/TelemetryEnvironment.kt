package dev.unlock.analytics

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import dev.unlock.BuildConfig
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

/**
 * Sammelt die standardisierten TelemetryDeck-Umgebungs-, Geräte-, Eingabehilfen- und
 * Kalender-Parameter ohne Drittanbieter-SDK und ohne personenbezogene Daten.
 */
object TelemetryEnvironment {

    private const val SDK_NAME = "UnlockAndroidClient"
    private val OS_VERSION_REGEX = Regex("""(\d+)(?:\.(\d+))?""")

    fun collect(
        context: Context,
        isDebug: Boolean = BuildConfig.DEBUG,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Map<String, String> = buildMap {
        val resources = context.resources
        val config = resources.configuration
        val dm = resources.displayMetrics

        // AppInfo
        put("TelemetryDeck.AppInfo.version", BuildConfig.VERSION_NAME)
        put("TelemetryDeck.AppInfo.buildNumber", BuildConfig.VERSION_CODE.toString())
        put("TelemetryDeck.AppInfo.versionAndBuildNumber", "${BuildConfig.VERSION_NAME} ${BuildConfig.VERSION_CODE}")

        // Device
        put("TelemetryDeck.Device.operatingSystem", "Android")
        put("TelemetryDeck.Device.platform", "Android")
        val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: Build.VERSION.SDK_INT.toString()
        val (majorVersion, majorMinorVersion) = parseOsVersions(release)
        put("TelemetryDeck.Device.systemVersion", release)
        put("TelemetryDeck.Device.systemMajorVersion", majorVersion)
        put("TelemetryDeck.Device.systemMajorMinorVersion", majorMinorVersion)

        normalizeBrand(Build.BRAND, Build.MANUFACTURER)?.let { put("TelemetryDeck.Device.brand", it) }
        Build.MODEL?.trim()?.takeIf { it.isNotBlank() }?.let { put("TelemetryDeck.Device.modelName", it) }
        val arch = Build.SUPPORTED_ABIS?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: System.getProperty("os.arch")?.takeIf { it.isNotBlank() }
        arch?.let { put("TelemetryDeck.Device.architecture", it) }

        if (dm.widthPixels > 0) put("TelemetryDeck.Device.screenResolutionWidth", dm.widthPixels.toString())
        if (dm.heightPixels > 0) put("TelemetryDeck.Device.screenResolutionHeight", dm.heightPixels.toString())
        if (dm.density > 0f) put("TelemetryDeck.Device.screenScaleFactor", formatDecimal(dm.density))
        if (dm.densityDpi > 0) put("TelemetryDeck.Device.screenDensity", densityBucket(dm.densityDpi))

        when (config.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> put("TelemetryDeck.Device.orientation", "Landscape")
            Configuration.ORIENTATION_PORTRAIT -> put("TelemetryDeck.Device.orientation", "Portrait")
        }
        put("TelemetryDeck.Device.timeZone", formatUtcOffset(now.offset))

        put("Unlock.Device.apiLevel", Build.VERSION.SDK_INT.toString())
        if (config.smallestScreenWidthDp > 0) {
            put("Unlock.Device.formFactor", if (config.smallestScreenWidthDp >= 600) "tablet" else "phone")
        }

        // RunContext
        val appLocale = config.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()
        formatLocale(appLocale)?.let { put("TelemetryDeck.RunContext.locale", it) }
        appLocale.language.takeIf { it.isNotBlank() }?.let { put("TelemetryDeck.RunContext.language", it) }

        val emulator = isEmulator()
        put("TelemetryDeck.RunContext.isDebug", isDebug.toString())
        put("TelemetryDeck.RunContext.isSimulator", emulator.toString())
        put(
            "TelemetryDeck.RunContext.targetEnvironment",
            when {
                isDebug -> "debug"
                emulator -> "simulator"
                else -> "production"
            },
        )

        val installer = readInstallerPackage(context)
        val (sourceMarketplace, isSideLoaded) = mapInstallSource(installer, context.packageName)
        put("TelemetryDeck.RunContext.sourceMarketplace", sourceMarketplace)
        put("TelemetryDeck.RunContext.isSideLoaded", isSideLoaded.toString())

        // UserPreference
        val sysLocale = Locale.getDefault()
        sysLocale.language.takeIf { it.isNotBlank() }?.let { put("TelemetryDeck.UserPreference.language", it) }
        sysLocale.country.takeIf { it.isNotBlank() }?.let { put("TelemetryDeck.UserPreference.region", it) }
        val isDark = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        put("TelemetryDeck.UserPreference.colorScheme", if (isDark) "Dark" else "Light")
        put(
            "TelemetryDeck.UserPreference.layoutDirection",
            if (config.layoutDirection == View.LAYOUT_DIRECTION_RTL) "rightToLeft" else "leftToRight",
        )

        // Accessibility
        put("TelemetryDeck.Accessibility.fontScale", formatDecimal(config.fontScale))
        put("TelemetryDeck.Accessibility.preferredContentSizeCategory", mapContentSizeCategory(config.fontScale))
        val boldText = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && config.fontWeightAdjustment > 0
        put("TelemetryDeck.Accessibility.isBoldTextEnabled", boldText.toString())
        val reduceMotion = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        put("TelemetryDeck.Accessibility.isReduceMotionEnabled", reduceMotion.toString())
        val invertColors = runCatching {
            Settings.Secure.getInt(context.contentResolver, "accessibility_display_inversion_enabled", 0) == 1
        }.getOrDefault(false)
        put("TelemetryDeck.Accessibility.isInvertColorsEnabled", invertColors.toString())
        val highContrast = runCatching {
            Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
        }.getOrDefault(false)
        put("TelemetryDeck.Accessibility.isDarkerSystemColorsEnabled", highContrast.toString())

        // Calendar
        putAll(calendarParams(now))

        // SDK
        put("TelemetryDeck.SDK.name", SDK_NAME)
        put("TelemetryDeck.SDK.version", BuildConfig.VERSION_NAME)
        put("TelemetryDeck.SDK.nameAndVersion", "$SDK_NAME ${BuildConfig.VERSION_NAME}")
    }

    private fun readInstallerPackage(context: Context): String? = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
    }.getOrNull()

    internal fun calendarParams(zdt: ZonedDateTime): Map<String, String> = mapOf(
        "TelemetryDeck.Calendar.dayOfMonth" to zdt.dayOfMonth.toString(),
        "TelemetryDeck.Calendar.dayOfWeek" to zdt.dayOfWeek.value.toString(),
        "TelemetryDeck.Calendar.dayOfYear" to zdt.dayOfYear.toString(),
        "TelemetryDeck.Calendar.weekOfYear" to zdt.get(WeekFields.ISO.weekOfWeekBasedYear()).toString(),
        "TelemetryDeck.Calendar.monthOfYear" to zdt.monthValue.toString(),
        "TelemetryDeck.Calendar.quarterOfYear" to (((zdt.monthValue - 1) / 3) + 1).toString(),
        // TelemetryDeck nutzt 1-basierte Stunden (1..24).
        "TelemetryDeck.Calendar.hourOfDay" to (zdt.hour + 1).toString(),
        "TelemetryDeck.Calendar.isWeekend" to (zdt.dayOfWeek.value >= 6).toString(),
    )

    internal fun parseOsVersions(release: String): Pair<String, String> {
        val match = OS_VERSION_REGEX.find(release)
        if (match == null) {
            val fallback = release.trim().ifBlank { "0" }
            return fallback to "$fallback.0"
        }
        val major = match.groupValues[1]
        val minor = match.groupValues[2].ifBlank { "0" }
        return major to "$major.$minor"
    }

    internal fun normalizeBrand(brand: String?, manufacturer: String?): String? {
        val raw = brand?.trim()?.takeIf { it.isNotBlank() }
            ?: manufacturer?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        return if (raw.none { it.isUpperCase() }) {
            raw.replaceFirstChar { it.titlecase(Locale.ROOT) }
        } else {
            raw
        }
    }

    internal fun formatDecimal(value: Float): String {
        val formatted = String.format(Locale.ROOT, "%.2f", value)
        return if (formatted.endsWith("0")) formatted.dropLast(1) else formatted
    }

    internal fun densityBucket(densityDpi: Int): String = when {
        densityDpi <= DisplayMetrics.DENSITY_LOW -> "ldpi"
        densityDpi <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
        densityDpi <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
        densityDpi <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
        densityDpi <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
        else -> "xxxhdpi"
    }

    internal fun formatUtcOffset(offset: ZoneOffset): String {
        val totalSeconds = offset.totalSeconds
        if (totalSeconds == 0) return "UTC+0"
        val sign = if (totalSeconds > 0) "+" else "-"
        val absSeconds = abs(totalSeconds)
        val hours = absSeconds / 3600
        val minutes = (absSeconds % 3600) / 60
        return if (minutes == 0) {
            "UTC$sign$hours"
        } else {
            String.format(Locale.ROOT, "UTC%s%d:%02d", sign, hours, minutes)
        }
    }

    internal fun formatLocale(locale: Locale): String? {
        val lang = locale.language.takeIf { it.isNotBlank() } ?: return null
        val country = locale.country.takeIf { it.isNotBlank() }
        return if (country != null) "${lang}_${country}" else lang
    }

    internal fun mapContentSizeCategory(fontScale: Float): String = when {
        fontScale <= 0.85f -> "S"
        fontScale <= 0.95f -> "M"
        fontScale <= 1.05f -> "L"
        fontScale <= 1.15f -> "XL"
        fontScale <= 1.25f -> "XXL"
        fontScale <= 1.35f -> "XXXL"
        fontScale <= 1.6f -> "AccessibilityM"
        else -> "AccessibilityL"
    }

    internal fun mapInstallSource(installerPackage: String?, ownPackageName: String): Pair<String, Boolean> {
        val pkg = installerPackage?.trim()?.takeIf { it.isNotBlank() } ?: return "Unknown" to true
        return when (pkg) {
            "com.android.vending" -> "GooglePlay" to false
            "org.fdroid.fdroid", "org.fdroid.fdroid.privileged", "org.fdroid.basic" -> "FDroid" to false
            "com.sec.android.app.samsungapps" -> "GalaxyStore" to false
            "com.amazon.venezia" -> "AmazonAppstore" to false
            "com.huawei.appmarket" -> "AppGallery" to false
            ownPackageName -> "SelfUpdate" to true
            "dev.imranr.obtainium", "dev.imranr.obtainium.fdroid" -> "Obtainium" to true
            "com.google.android.packageinstaller", "com.android.packageinstaller", "com.android.shell" -> "Sideload" to true
            else -> "Other" to true
        }
    }

    internal fun isEmulator(
        fingerprint: String = Build.FINGERPRINT.orEmpty(),
        model: String = Build.MODEL.orEmpty(),
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty(),
        device: String = Build.DEVICE.orEmpty(),
        product: String = Build.PRODUCT.orEmpty(),
        hardware: String = Build.HARDWARE.orEmpty(),
    ): Boolean =
        fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            "google_sdk" in model ||
            "Emulator" in model ||
            "Android SDK built for x86" in model ||
            "sdk_gphone" in model ||
            "Genymotion" in manufacturer ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product == "google_sdk" ||
            product.startsWith("sdk_") ||
            product.startsWith("vbox") ||
            product == "emulator" ||
            product == "simulator" ||
            hardware == "goldfish" ||
            hardware == "ranchu" ||
            "vbox" in hardware
}
