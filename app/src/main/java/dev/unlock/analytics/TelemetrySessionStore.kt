package dev.unlock.analytics

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Lokaler, nicht gesicherter Sitzungs- und Retention-Speicher für TelemetryDeck.
 *
 * Speichert ausschließlich anonyme Zähler und Datumsangaben (keine URLs, keine PII)
 * und wird beim Opt-out vollständig gelöscht.
 */
class TelemetrySessionStore(
    context: Context,
    prefsName: String = PREFS_NAME,
) {

    data class SessionTouchResult(
        val sessionId: String,
        val isNewSession: Boolean,
        val isNewInstall: Boolean,
        val retentionParams: Map<String, String>,
    )

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private var activeSessionId: String? = null
    private var activeSessionStartMs: Long = 0L
    private var activeSessionDate: LocalDate? = null
    private var lastActivityMs: Long = 0L

    @Synchronized
    fun touch(nowMs: Long, today: LocalDate = LocalDate.now()): SessionTouchResult {
        val isNewSession = activeSessionId == null ||
            (nowMs - lastActivityMs) >= SESSION_TIMEOUT_MS ||
            activeSessionDate != today

        var firstSessionDate = prefs.getString(KEY_FIRST_SESSION_DATE, null)
        var lastActiveDate = prefs.getString(KEY_LAST_ACTIVE_DATE, null)
        var distinctDaysUsed = prefs.getInt(KEY_DISTINCT_DAYS_USED, 0)
        var totalSessionsCount = prefs.getInt(KEY_TOTAL_SESSIONS_COUNT, 0)
        var completedSessionsCount = prefs.getInt(KEY_COMPLETED_SESSIONS_COUNT, 0)
        var completedSessionsDurationSec = prefs.getLong(KEY_COMPLETED_SESSIONS_DURATION_SEC, 0L)
        var previousSessionSec = prefs.getLong(KEY_PREVIOUS_SESSION_SEC, -1L)
        var persistedSessionStartMs = prefs.getLong(KEY_PERSISTED_SESSION_START_MS, 0L)
        val persistedLastActivityMs = prefs.getLong(KEY_PERSISTED_LAST_ACTIVITY_MS, 0L)
        var recentDays = parseDates(prefs.getString(KEY_RECENT_ACTIVE_DAYS, null))

        var isNewInstall = false

        if (isNewSession) {
            if (persistedSessionStartMs > 0L && persistedLastActivityMs >= persistedSessionStartMs) {
                val prevSec = ((persistedLastActivityMs - persistedSessionStartMs) / 1000L)
                    .coerceIn(0L, MAX_SESSION_SECONDS)
                previousSessionSec = prevSec
                completedSessionsCount += 1
                completedSessionsDurationSec += prevSec
            }
            if (firstSessionDate == null) {
                isNewInstall = true
                firstSessionDate = today.toString()
            }
            totalSessionsCount += 1
            activeSessionId = UUID.randomUUID().toString()
            activeSessionStartMs = nowMs
            activeSessionDate = today
            persistedSessionStartMs = nowMs
        }

        lastActivityMs = nowMs
        val todayStr = today.toString()
        if (todayStr != lastActiveDate) {
            distinctDaysUsed += 1
            lastActiveDate = todayStr
            val cutoff = today.minusDays(RECENT_DAYS_RETENTION)
            recentDays = (recentDays + today).filter { !it.isBefore(cutoff) }.distinct().sorted()
        }

        prefs.edit {
            putString(KEY_FIRST_SESSION_DATE, firstSessionDate)
            putString(KEY_LAST_ACTIVE_DATE, lastActiveDate)
            putInt(KEY_DISTINCT_DAYS_USED, distinctDaysUsed)
            putString(KEY_RECENT_ACTIVE_DAYS, recentDays.joinToString("\n") { it.toString() })
            putInt(KEY_TOTAL_SESSIONS_COUNT, totalSessionsCount)
            putInt(KEY_COMPLETED_SESSIONS_COUNT, completedSessionsCount)
            putLong(KEY_COMPLETED_SESSIONS_DURATION_SEC, completedSessionsDurationSec)
            putLong(KEY_PREVIOUS_SESSION_SEC, previousSessionSec)
            putLong(KEY_PERSISTED_SESSION_START_MS, persistedSessionStartMs)
            putLong(KEY_PERSISTED_LAST_ACTIVITY_MS, nowMs)
        }

        val prevMonth = YearMonth.from(today).minusMonths(1)
        val distinctDaysLastMonth = recentDays.count { YearMonth.from(it) == prevMonth }

        val retentionParams = buildMap {
            firstSessionDate?.let { put("TelemetryDeck.Acquisition.firstSessionDate", it) }
            put("TelemetryDeck.Retention.distinctDaysUsed", distinctDaysUsed.toString())
            put("TelemetryDeck.Retention.distinctDaysUsedLastMonth", distinctDaysLastMonth.toString())
            put("TelemetryDeck.Retention.totalSessionsCount", totalSessionsCount.toString())
            if (previousSessionSec >= 0L) {
                put("TelemetryDeck.Retention.previousSessionSeconds", previousSessionSec.toString())
            }
            if (completedSessionsCount > 0) {
                val avgSec = completedSessionsDurationSec / completedSessionsCount
                put("TelemetryDeck.Retention.averageSessionSeconds", avgSec.toString())
            }
        }

        return SessionTouchResult(
            sessionId = activeSessionId!!,
            isNewSession = isNewSession,
            isNewInstall = isNewInstall,
            retentionParams = retentionParams,
        )
    }

    @Synchronized
    fun clear() {
        activeSessionId = null
        activeSessionStartMs = 0L
        activeSessionDate = null
        lastActivityMs = 0L
        prefs.edit { clear() }
    }

    private fun parseDates(raw: String?): List<LocalDate> =
        raw.orEmpty().lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { LocalDate.parse(line) }.getOrNull() }
            .toList()

    companion object {
        internal const val PREFS_NAME = "telemetry_state"
        internal const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L
        internal const val MAX_SESSION_SECONDS = 24 * 3600L
        private const val RECENT_DAYS_RETENTION = 65L

        private const val KEY_FIRST_SESSION_DATE = "first_session_date"
        private const val KEY_LAST_ACTIVE_DATE = "last_active_date"
        private const val KEY_DISTINCT_DAYS_USED = "distinct_days_used"
        private const val KEY_RECENT_ACTIVE_DAYS = "recent_active_days"
        private const val KEY_TOTAL_SESSIONS_COUNT = "total_sessions_count"
        private const val KEY_COMPLETED_SESSIONS_COUNT = "completed_sessions_count"
        private const val KEY_COMPLETED_SESSIONS_DURATION_SEC = "completed_sessions_duration_sec"
        private const val KEY_PREVIOUS_SESSION_SEC = "previous_session_sec"
        private const val KEY_PERSISTED_SESSION_START_MS = "persisted_session_start_ms"
        private const val KEY_PERSISTED_LAST_ACTIVITY_MS = "persisted_last_activity_ms"
    }
}
