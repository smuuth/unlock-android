package dev.unlock.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UnlockStatsTest {

    private val today = LocalDate.of(2026, 11, 24) // Dienstag
    private fun day(offset: Long) = today.minusDays(offset).toEpochDay()
    private val event = UnlockEvent("share", "archive_today", "spiegel.de", 2)

    @Test fun applyCountsEverything() {
        var s = UnlockStats()
        s = StatsMath.apply(s, event, day(0), 22, 1000L)
        s = StatsMath.apply(s, event.copy(source = "clipboard", host = null, trackersRemoved = 0), day(0), 7, 2000L)
        assertEquals(2, s.total)
        assertEquals(1000L, s.firstUnlockAt)
        assertEquals(2, s.countOn(today))
        assertEquals(1, s.hourly[22])
        assertEquals(1, s.hourly[7])
        assertEquals(mapOf("share" to 1, "clipboard" to 1), s.sources)
        assertEquals(mapOf("spiegel.de" to 1), s.hosts)
        assertEquals(2, s.trackersRemoved)
        assertEquals(2 * UnlockStats.DEFAULT_PRICE_CENTS.toLong(), s.savedCents)
    }

    @Test fun streaks() {
        val s = UnlockStats(daily = mapOf(day(1) to 1, day(2) to 3, day(3) to 1, day(10) to 1, day(11) to 1))
        assertEquals(3, s.currentStreak(today)) // gestern hält die Serie
        assertEquals(3, s.longestStreak())
        assertEquals(0, UnlockStats(daily = mapOf(day(2) to 1)).currentStreak(today))
        assertEquals(4, s.copy(daily = s.daily + (day(0) to 1)).currentStreak(today))
    }

    @Test fun seriesAndAggregates() {
        val s = UnlockStats(daily = mapOf(day(0) to 2, day(6) to 1, day(40) to 5), hourly = List(24) { if (it == 21) 4 else 0 })
        val week = s.dailySeries(today, 7)
        assertEquals(7, week.size)
        assertEquals(today, week.last().first)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 2), week.map { it.second })
        assertEquals(2, s.thisWeek(today)) // nur Di 24.11.; der Mittwoch davor gehört zur Vorwoche
        assertEquals(12, s.monthlySeries(today, 12).size)
        assertEquals(5, s.monthlySeries(today, 12).first { it.first.monthValue == 10 }.second)
        assertEquals(21, s.peakHour())
        assertEquals(LocalDate.ofEpochDay(day(40)) to 5, s.bestDay())
        assertEquals(8, s.weekdayTotals().sum())
        assertNull(UnlockStats().peakHour())
    }

    @Test fun prunesOldDays() {
        val s = StatsMath.apply(UnlockStats(daily = mapOf(day(500) to 9)), event, day(0), 0, 0L)
        assertEquals(setOf(day(0)), s.daily.keys)
    }

    @Test fun removedParamsAndHosts() {
        assertEquals(2, StatsMath.removedParams("https://a.de/x?utm_source=1&utm_medium=2&id=3", "https://a.de/x?id=3"))
        assertEquals(0, StatsMath.removedParams("https://a.de/x", "https://a.de/x"))
        assertEquals("spiegel.de", StatsMath.hostOf("https://www.spiegel.de/politik/a-123"))
        assertEquals("nytimes.com", StatsMath.hostOf("https://m.nytimes.com/x"))
        assertNull(StatsMath.hostOf("not a url"))
    }

    @Test fun codecRoundTrip() {
        val counts = mapOf("share" to 3, "process_text" to 1)
        assertEquals(counts, StatsMath.decodeCounts(StatsMath.encodeCounts(counts)))
        val daily = mapOf(20000L to 2, 20001L to 5)
        assertEquals(daily, StatsMath.decodeDaily(StatsMath.encodeDaily(daily)))
        val hourly = List(24) { it }
        assertEquals(hourly, StatsMath.decodeHourly(StatsMath.encodeHourly(hourly)))
        assertEquals(List(24) { 0 }, StatsMath.decodeHourly(null))
        assertEquals(emptyMap<String, Int>(), StatsMath.decodeCounts(""))
    }
}
