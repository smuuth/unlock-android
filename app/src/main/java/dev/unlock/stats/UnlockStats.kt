package dev.unlock.stats

import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Lokale Nutzungsstatistik – verlässt nie das Gerät.
 *
 * Gespeichert werden nur Zähler: pro Tag, pro Stunde, pro Quelle/Dienst und pro
 * Website (Host ohne Pfad). Keine vollständigen URLs, keine Titel.
 */
data class UnlockStats(
    val enabled: Boolean = true,
    val total: Int = 0,
    val firstUnlockAt: Long? = null,
    /** epochDay → Anzahl. */
    val daily: Map<Long, Int> = emptyMap(),
    /** 24 Einträge, Index = Stunde (lokale Zeit). */
    val hourly: List<Int> = List(24) { 0 },
    val sources: Map<String, Int> = emptyMap(),
    val services: Map<String, Int> = emptyMap(),
    val hosts: Map<String, Int> = emptyMap(),
    val trackersRemoved: Int = 0,
    val readerArticles: Int = 0,
    val readerWords: Long = 0,
    val readerMinutes: Int = 0,
) {
    val isEmpty: Boolean get() = total == 0

    fun countOn(day: LocalDate): Int = daily[day.toEpochDay()] ?: 0

    /** Die letzten [days] Tage bis einschließlich [today], älteste zuerst. */
    fun dailySeries(today: LocalDate, days: Int): List<Pair<LocalDate, Int>> =
        (days - 1 downTo 0).map { offset -> today.minusDays(offset.toLong()).let { it to countOn(it) } }

    /** Die letzten [months] Monate bis einschließlich des aktuellen, älteste zuerst. */
    fun monthlySeries(today: LocalDate, months: Int): List<Pair<YearMonth, Int>> {
        val byMonth = HashMap<YearMonth, Int>()
        daily.forEach { (day, count) ->
            val ym = YearMonth.from(LocalDate.ofEpochDay(day))
            byMonth[ym] = (byMonth[ym] ?: 0) + count
        }
        val current = YearMonth.from(today)
        return (months - 1 downTo 0).map { offset -> current.minusMonths(offset.toLong()).let { it to (byMonth[it] ?: 0) } }
    }

    /** Summe pro Wochentag, Montag zuerst. */
    fun weekdayTotals(): List<Int> {
        val totals = IntArray(7)
        daily.forEach { (day, count) -> totals[LocalDate.ofEpochDay(day).dayOfWeek.value - 1] += count }
        return totals.toList()
    }

    fun thisWeek(today: LocalDate): Int {
        val monday = today.with(DayOfWeek.MONDAY)
        return daily.entries.sumOf { (day, count) -> if (day in monday.toEpochDay()..today.toEpochDay()) count else 0 }
    }

    /** Aufeinanderfolgende Tage mit mindestens einem geöffneten Artikel – heute zählt, gestern hält die Serie noch am Leben. */
    fun currentStreak(today: LocalDate): Int {
        var day = if (countOn(today) > 0) today else today.minusDays(1)
        var streak = 0
        while (countOn(day) > 0) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun longestStreak(): Int {
        var best = 0
        var run = 0
        var previous: Long? = null
        daily.filterValues { it > 0 }.keys.sorted().forEach { day ->
            run = if (previous != null && day == previous!! + 1) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    fun bestDay(): Pair<LocalDate, Int>? =
        daily.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { LocalDate.ofEpochDay(it.key) to it.value }

    /** Stunde mit den meisten geöffneten Artikeln oder `null`, wenn noch nichts erfasst wurde. */
    fun peakHour(): Int? = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 }

    fun topHosts(limit: Int): List<Pair<String, Int>> =
        hosts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key to it.value }

    companion object {
        /** Durchschnittlicher Roman ≈ 90 000 Wörter. */
        const val WORDS_PER_NOVEL = 90_000
    }
}

/** Ein einzelnes erfolgreich geöffnetes Archiv. */
data class UnlockEvent(
    val source: String,
    val service: String,
    val host: String?,
    val trackersRemoved: Int,
)

/** Reine Hilfsfunktionen für die Erfassung (ohne Android, testbar). */
object StatsMath {

    /** Anzahl der Query-Parameter, die beim Bereinigen weggefallen sind. */
    fun removedParams(original: String, cleaned: String): Int =
        (paramCount(original) - paramCount(cleaned)).coerceAtLeast(0)

    private fun paramCount(url: String): Int =
        url.substringBefore('#').substringAfter('?', "").split('&').count { it.isNotBlank() }

    /** Host ohne „www.“ – nur die Website, nie der Pfad. */
    fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()
        ?.lowercase()
        ?.removePrefix("www.")
        ?.removePrefix("m.")
        ?.takeIf { it.isNotBlank() && '=' !in it && ';' !in it }

    // Format: "key=value;key=value". Keys enthalten weder '=' noch ';'.
    fun encodeCounts(map: Map<String, Int>): String = map.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decodeCounts(raw: String?): Map<String, Int> = raw.orEmpty().split(';').mapNotNull { part ->
        val key = part.substringBefore('=', "")
        val value = part.substringAfter('=', "").toIntOrNull()
        if (key.isBlank() || value == null) null else key to value
    }.toMap()

    fun encodeDaily(map: Map<Long, Int>): String = map.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

    fun decodeDaily(raw: String?): Map<Long, Int> =
        decodeCounts(raw).mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }.toMap()

    fun encodeHourly(list: List<Int>): String = list.joinToString(",")

    fun decodeHourly(raw: String?): List<Int> {
        val parsed = raw.orEmpty().split(',').map { it.toIntOrNull() ?: 0 }
        return List(24) { parsed.getOrElse(it) { 0 } }
    }

    /** Reiht ein Ereignis in die Zähler ein; alte Tage und seltene Hosts werden gekappt. */
    fun apply(stats: UnlockStats, event: UnlockEvent, epochDay: Long, hour: Int, now: Long): UnlockStats {
        val daily = stats.daily.toMutableMap().apply {
            merge(epochDay, 1, Int::plus)
            keys.removeAll { it < epochDay - MAX_DAYS }
        }
        val hosts = stats.hosts.toMutableMap().apply {
            event.host?.let { merge(it, 1, Int::plus) }
        }.let { if (it.size > MAX_HOSTS) it.entries.sortedByDescending { e -> e.value }.take(MAX_HOSTS / 2).associate { e -> e.key to e.value } else it }
        return stats.copy(
            total = stats.total + 1,
            firstUnlockAt = stats.firstUnlockAt ?: now,
            daily = daily,
            hourly = stats.hourly.mapIndexed { i, c -> if (i == hour) c + 1 else c },
            sources = stats.sources + (event.source to (stats.sources[event.source] ?: 0) + 1),
            services = stats.services + (event.service to (stats.services[event.service] ?: 0) + 1),
            hosts = hosts,
            trackersRemoved = stats.trackersRemoved + event.trackersRemoved,
        )
    }

    const val MAX_DAYS = 400L
    const val MAX_HOSTS = 120
}
