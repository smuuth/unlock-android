package dev.unlock.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test fun stripsLeadingV() {
        assertEquals("1.2.0", AppVersion.normalize("v1.2.0"))
        assertEquals("1.2.0", AppVersion.normalize(" V1.2.0 "))
        assertEquals("1.2.0", AppVersion.normalize("1.2.0"))
    }

    @Test fun newerPatchMinorMajor() {
        assertTrue(AppVersion.isNewer("v1.2.0", "1.1.0"))
        assertTrue(AppVersion.isNewer("1.1.1", "1.1.0"))
        assertTrue(AppVersion.isNewer("2.0.0", "1.9.9"))
    }

    @Test fun comparesNumericallyNotLexically() {
        assertTrue(AppVersion.isNewer("1.10.0", "1.9.0"))
        assertFalse(AppVersion.isNewer("1.9.0", "1.10.0"))
    }

    @Test fun sameOrOlderIsNotNewer() {
        assertFalse(AppVersion.isNewer("v1.2.0", "1.2.0"))
        assertFalse(AppVersion.isNewer("1.1.0", "1.2.0"))
    }

    @Test fun missingPartsCountAsZero() {
        assertEquals(0, AppVersion.compare("1.2", "1.2.0"))
        assertFalse(AppVersion.isNewer("1.2", "1.2.0"))
        assertTrue(AppVersion.isNewer("1.2.0.1", "1.2"))
    }

    @Test fun preReleaseIsOlderThanFinal() {
        assertTrue(AppVersion.compare("1.3.0-beta.1", "1.3.0") < 0)
        assertTrue(AppVersion.isNewer("1.3.0", "1.3.0-beta.1"))
        assertTrue(AppVersion.isNewer("1.3.0-beta.1", "1.2.0"))
        assertTrue(AppVersion.isNewer("1.3.0-rc.1", "1.3.0-beta.2"))
    }

    @Test fun buildMetadataIsIgnored() {
        assertEquals(0, AppVersion.compare("1.2.0+42", "1.2.0"))
    }

    @Test fun garbageIsNeverNewer() {
        assertFalse(AppVersion.isNewer("latest", "1.1.0"))
        assertFalse(AppVersion.isNewer("", "1.1.0"))
        assertFalse(AppVersion.isNewer("1.x.0", "1.1.0"))
        assertFalse(AppVersion.isNewer("-1.0", "1.1.0"))
        assertFalse(AppVersion.isNewer("2.0.0", "dev"))
    }

    @Test fun scheduleRunsAtMostAboutOncePerDay() {
        val hour = 60L * 60 * 1000
        val last = 1_000_000_000_000L
        assertTrue(UpdateSchedule.isDue(lastCheckMs = 0L, nowMs = last))
        assertFalse(UpdateSchedule.isDue(last, last + 1 * hour))
        assertFalse(UpdateSchedule.isDue(last, last + 19 * hour))
        assertTrue(UpdateSchedule.isDue(last, last + 20 * hour))
        assertTrue(UpdateSchedule.isDue(last, last + 48 * hour))
        // Uhr zurückgestellt → lieber prüfen als ewig warten.
        assertTrue(UpdateSchedule.isDue(last, last - hour))
    }
}
