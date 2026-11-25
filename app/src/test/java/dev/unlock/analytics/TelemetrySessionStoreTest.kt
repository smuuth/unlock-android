package dev.unlock.analytics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TelemetrySessionStoreTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var store: TelemetrySessionStore

    @Before fun setUp() {
        store = TelemetrySessionStore(app)
        store.clear()
    }

    @Test fun firstTouchMarksNewInstallAndSession() {
        val day = LocalDate.of(2026, 11, 25)
        val first = store.touch(nowMs = 1_000_000L, today = day)

        assertTrue(first.isNewInstall)
        assertTrue(first.isNewSession)
        assertEquals("2026-11-25", first.retentionParams["TelemetryDeck.Acquisition.firstSessionDate"])
        assertEquals("1", first.retentionParams["TelemetryDeck.Retention.distinctDaysUsed"])
        assertEquals("0", first.retentionParams["TelemetryDeck.Retention.distinctDaysUsedLastMonth"])
        assertEquals("1", first.retentionParams["TelemetryDeck.Retention.totalSessionsCount"])
        assertNull(first.retentionParams["TelemetryDeck.Retention.previousSessionSeconds"])
        assertNull(first.retentionParams["TelemetryDeck.Retention.averageSessionSeconds"])
    }

    @Test fun touchWithinTimeoutReusesSessionAndTimeoutStartsNewOneWithDurations() {
        val day = LocalDate.of(2026, 11, 25)
        val t0 = 1_000_000L
        val r1 = store.touch(nowMs = t0, today = day)
        val r2 = store.touch(nowMs = t0 + 90_000L, today = day)

        assertEquals(r1.sessionId, r2.sessionId)
        assertFalse(r2.isNewInstall)
        assertFalse(r2.isNewSession)
        assertEquals("1", r2.retentionParams["TelemetryDeck.Retention.totalSessionsCount"])

        val r3 = store.touch(nowMs = t0 + 90_000L + TelemetrySessionStore.SESSION_TIMEOUT_MS, today = day)
        assertNotEquals(r1.sessionId, r3.sessionId)
        assertFalse(r3.isNewInstall)
        assertTrue(r3.isNewSession)
        assertEquals("2", r3.retentionParams["TelemetryDeck.Retention.totalSessionsCount"])
        assertEquals("90", r3.retentionParams["TelemetryDeck.Retention.previousSessionSeconds"])
        assertEquals("90", r3.retentionParams["TelemetryDeck.Retention.averageSessionSeconds"])
    }

    @Test fun multiDayAndCrossMonthCountsDistinctDaysUsedLastMonth() {
        val oct10 = LocalDate.of(2026, 10, 10)
        val oct20 = LocalDate.of(2026, 10, 20)
        val nov10 = LocalDate.of(2026, 11, 10)

        store.touch(nowMs = 1_000_000L, today = oct10)
        store.touch(nowMs = 2_000_000L, today = oct20)
        val novResult = store.touch(nowMs = 3_000_000L, today = nov10)

        assertEquals("2026-10-10", novResult.retentionParams["TelemetryDeck.Acquisition.firstSessionDate"])
        assertEquals("3", novResult.retentionParams["TelemetryDeck.Retention.distinctDaysUsed"])
        assertEquals("2", novResult.retentionParams["TelemetryDeck.Retention.distinctDaysUsedLastMonth"])
        assertEquals("3", novResult.retentionParams["TelemetryDeck.Retention.totalSessionsCount"])
    }

    @Test fun clearResetsAllPersistedState() {
        val day1 = LocalDate.of(2026, 11, 20)
        val r1 = store.touch(nowMs = 1_000_000L, today = day1)
        assertTrue(r1.isNewInstall)

        store.clear()

        val day2 = LocalDate.of(2026, 11, 25)
        val r2 = store.touch(nowMs = 2_000_000L, today = day2)
        assertTrue(r2.isNewInstall)
        assertTrue(r2.isNewSession)
        assertEquals("2026-11-25", r2.retentionParams["TelemetryDeck.Acquisition.firstSessionDate"])
        assertEquals("1", r2.retentionParams["TelemetryDeck.Retention.distinctDaysUsed"])
        assertEquals("1", r2.retentionParams["TelemetryDeck.Retention.totalSessionsCount"])
        assertNull(r2.retentionParams["TelemetryDeck.Retention.previousSessionSeconds"])
    }
}
