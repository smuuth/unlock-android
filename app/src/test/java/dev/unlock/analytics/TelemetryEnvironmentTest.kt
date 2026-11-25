package dev.unlock.analytics

import android.app.Application
import android.util.DisplayMetrics
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TelemetryEnvironmentTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun collectIncludesStandardTelemetryDeckKeysAndExcludesSystemName() {
        val now = ZonedDateTime.of(2026, 11, 25, 11, 22, 0, 0, ZoneOffset.ofHours(1))
        val params = TelemetryEnvironment.collect(app, isDebug = true, now = now)

        assertFalse(params.containsKey("TelemetryDeck.Device.systemName"))

        val expectedKeys = listOf(
            "TelemetryDeck.AppInfo.version",
            "TelemetryDeck.AppInfo.buildNumber",
            "TelemetryDeck.AppInfo.versionAndBuildNumber",
            "TelemetryDeck.Device.operatingSystem",
            "TelemetryDeck.Device.platform",
            "TelemetryDeck.Device.systemVersion",
            "TelemetryDeck.Device.systemMajorVersion",
            "TelemetryDeck.Device.systemMajorMinorVersion",
            "TelemetryDeck.Device.timeZone",
            "TelemetryDeck.RunContext.isDebug",
            "TelemetryDeck.RunContext.isSimulator",
            "TelemetryDeck.RunContext.targetEnvironment",
            "TelemetryDeck.RunContext.sourceMarketplace",
            "TelemetryDeck.RunContext.isSideLoaded",
            "TelemetryDeck.UserPreference.colorScheme",
            "TelemetryDeck.UserPreference.layoutDirection",
            "TelemetryDeck.Accessibility.fontScale",
            "TelemetryDeck.Accessibility.preferredContentSizeCategory",
            "TelemetryDeck.Accessibility.isBoldTextEnabled",
            "TelemetryDeck.Accessibility.isReduceMotionEnabled",
            "TelemetryDeck.Accessibility.isInvertColorsEnabled",
            "TelemetryDeck.Accessibility.isDarkerSystemColorsEnabled",
            "TelemetryDeck.Calendar.dayOfMonth",
            "TelemetryDeck.Calendar.dayOfWeek",
            "TelemetryDeck.Calendar.dayOfYear",
            "TelemetryDeck.Calendar.weekOfYear",
            "TelemetryDeck.Calendar.monthOfYear",
            "TelemetryDeck.Calendar.quarterOfYear",
            "TelemetryDeck.Calendar.hourOfDay",
            "TelemetryDeck.Calendar.isWeekend",
            "TelemetryDeck.SDK.name",
            "TelemetryDeck.SDK.version",
            "TelemetryDeck.SDK.nameAndVersion",
            "Unlock.Device.apiLevel",
        )
        for (key in expectedKeys) {
            assertTrue("Missing key $key", params.containsKey(key))
        }
        assertEquals("Android", params["TelemetryDeck.Device.operatingSystem"])
        assertEquals("Android", params["TelemetryDeck.Device.platform"])
        assertEquals("UTC+1", params["TelemetryDeck.Device.timeZone"])
        assertEquals("debug", params["TelemetryDeck.RunContext.targetEnvironment"])
        assertEquals("UnlockAndroidClient", params["TelemetryDeck.SDK.name"])
    }

    @Test fun parseOsVersionsHandlesSingleAndDottedVersions() {
        assertEquals("15" to "15.0", TelemetryEnvironment.parseOsVersions("15"))
        assertEquals("14" to "14.1", TelemetryEnvironment.parseOsVersions("14.1"))
        assertEquals("13" to "13.2", TelemetryEnvironment.parseOsVersions("13.2.1"))
        assertEquals("0" to "0.0", TelemetryEnvironment.parseOsVersions(""))
    }

    @Test fun normalizeBrandCapitalizesLowercaseAndPreservesMixedCase() {
        assertEquals("Google", TelemetryEnvironment.normalizeBrand("google", "Google"))
        assertEquals("Samsung", TelemetryEnvironment.normalizeBrand("samsung", "samsung"))
        assertEquals("OnePlus", TelemetryEnvironment.normalizeBrand("OnePlus", "OnePlus"))
        assertEquals("ASUS", TelemetryEnvironment.normalizeBrand("ASUS", "asus"))
        assertEquals("Fairphone", TelemetryEnvironment.normalizeBrand("  ", "fairphone"))
        assertNull(TelemetryEnvironment.normalizeBrand("", null))
    }

    @Test fun formatUtcOffsetHandlesZeroWholeAndHalfHours() {
        assertEquals("UTC+0", TelemetryEnvironment.formatUtcOffset(ZoneOffset.UTC))
        assertEquals("UTC+1", TelemetryEnvironment.formatUtcOffset(ZoneOffset.ofHours(1)))
        assertEquals("UTC-5", TelemetryEnvironment.formatUtcOffset(ZoneOffset.ofHours(-5)))
        assertEquals("UTC+5:30", TelemetryEnvironment.formatUtcOffset(ZoneOffset.ofHoursMinutes(5, 30)))
    }

    @Test fun mapContentSizeCategoryCoversAllBuckets() {
        assertEquals("S", TelemetryEnvironment.mapContentSizeCategory(0.85f))
        assertEquals("M", TelemetryEnvironment.mapContentSizeCategory(0.95f))
        assertEquals("L", TelemetryEnvironment.mapContentSizeCategory(1.0f))
        assertEquals("XL", TelemetryEnvironment.mapContentSizeCategory(1.15f))
        assertEquals("XXL", TelemetryEnvironment.mapContentSizeCategory(1.25f))
        assertEquals("XXXL", TelemetryEnvironment.mapContentSizeCategory(1.35f))
        assertEquals("AccessibilityM", TelemetryEnvironment.mapContentSizeCategory(1.5f))
        assertEquals("AccessibilityL", TelemetryEnvironment.mapContentSizeCategory(2.0f))
    }

    @Test fun mapInstallSourceCategorizesStoresAndSideloads() {
        assertEquals("GooglePlay" to false, TelemetryEnvironment.mapInstallSource("com.android.vending", "dev.unlock"))
        assertEquals("FDroid" to false, TelemetryEnvironment.mapInstallSource("org.fdroid.fdroid", "dev.unlock"))
        assertEquals("GalaxyStore" to false, TelemetryEnvironment.mapInstallSource("com.sec.android.app.samsungapps", "dev.unlock"))
        assertEquals("AmazonAppstore" to false, TelemetryEnvironment.mapInstallSource("com.amazon.venezia", "dev.unlock"))
        assertEquals("AppGallery" to false, TelemetryEnvironment.mapInstallSource("com.huawei.appmarket", "dev.unlock"))
        assertEquals("SelfUpdate" to true, TelemetryEnvironment.mapInstallSource("dev.unlock", "dev.unlock"))
        assertEquals("Obtainium" to true, TelemetryEnvironment.mapInstallSource("dev.imranr.obtainium", "dev.unlock"))
        assertEquals("Sideload" to true, TelemetryEnvironment.mapInstallSource("com.google.android.packageinstaller", "dev.unlock"))
        assertEquals("Unknown" to true, TelemetryEnvironment.mapInstallSource(null, "dev.unlock"))
        assertEquals("Other" to true, TelemetryEnvironment.mapInstallSource("com.custom.store", "dev.unlock"))
    }

    @Test fun isEmulatorDetectsKnownEmulatorSignatures() {
        assertTrue(
            TelemetryEnvironment.isEmulator(
                fingerprint = "google/sdk_gphone64_arm64/emu64a:15/...",
                model = "sdk_gphone64_arm64",
                manufacturer = "Google",
                brand = "google",
                device = "emu64a",
                product = "sdk_gphone64_arm64",
                hardware = "ranchu",
            ),
        )
        assertFalse(
            TelemetryEnvironment.isEmulator(
                fingerprint = "google/husky/husky:15/AP3A.241005.015/1234:user/release-keys",
                model = "Pixel 8 Pro",
                manufacturer = "Google",
                brand = "google",
                device = "husky",
                product = "husky",
                hardware = "husky",
            ),
        )
    }

    @Test fun calendarParamsUseOneBasedHourAndIsoWeekdays() {
        val midnightWednesday = ZonedDateTime.of(2026, 11, 25, 0, 15, 0, 0, ZoneId.of("Europe/Berlin"))
        val p1 = TelemetryEnvironment.calendarParams(midnightWednesday)
        assertEquals("25", p1["TelemetryDeck.Calendar.dayOfMonth"])
        assertEquals("3", p1["TelemetryDeck.Calendar.dayOfWeek"])
        assertEquals("11", p1["TelemetryDeck.Calendar.monthOfYear"])
        assertEquals("4", p1["TelemetryDeck.Calendar.quarterOfYear"])
        assertEquals("1", p1["TelemetryDeck.Calendar.hourOfDay"])
        assertEquals("false", p1["TelemetryDeck.Calendar.isWeekend"])

        val lateSaturday = ZonedDateTime.of(2026, 1, 3, 23, 45, 0, 0, ZoneId.of("UTC"))
        val p2 = TelemetryEnvironment.calendarParams(lateSaturday)
        assertEquals("6", p2["TelemetryDeck.Calendar.dayOfWeek"])
        assertEquals("1", p2["TelemetryDeck.Calendar.quarterOfYear"])
        assertEquals("24", p2["TelemetryDeck.Calendar.hourOfDay"])
        assertEquals("true", p2["TelemetryDeck.Calendar.isWeekend"])
    }

    @Test fun densityBucketAndFormatDecimalWorkCleanly() {
        assertEquals("ldpi", TelemetryEnvironment.densityBucket(DisplayMetrics.DENSITY_LOW))
        assertEquals("mdpi", TelemetryEnvironment.densityBucket(DisplayMetrics.DENSITY_MEDIUM))
        assertEquals("hdpi", TelemetryEnvironment.densityBucket(DisplayMetrics.DENSITY_HIGH))
        assertEquals("xhdpi", TelemetryEnvironment.densityBucket(DisplayMetrics.DENSITY_XHIGH))
        assertEquals("xxhdpi", TelemetryEnvironment.densityBucket(DisplayMetrics.DENSITY_XXHIGH))
        assertEquals("xxxhdpi", TelemetryEnvironment.densityBucket(DisplayMetrics.DENSITY_XXXHIGH))
        assertEquals("3.0", TelemetryEnvironment.formatDecimal(3.0f))
        assertEquals("2.75", TelemetryEnvironment.formatDecimal(2.75f))
        assertEquals("1.05", TelemetryEnvironment.formatDecimal(1.05f))
    }
}
