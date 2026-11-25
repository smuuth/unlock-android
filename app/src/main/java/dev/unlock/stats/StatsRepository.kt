package dev.unlock.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

private val Context.statsStore: DataStore<Preferences> by preferencesDataStore(name = "stats")

/**
 * Eigene DataStore-Datei für die lokale Statistik – getrennt von den Einstellungen
 * und bewusst nicht im Backup (siehe `backup_rules.xml`).
 */
class StatsRepository(context: Context) {

    private val store = context.applicationContext.statsStore

    val stats: Flow<UnlockStats> = store.data.map { it.toStats() }.distinctUntilChanged()

    suspend fun record(event: UnlockEvent, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) =
        store.edit { prefs ->
            val current = prefs.toStats()
            if (!current.enabled) return@edit
            val time = Instant.ofEpochMilli(now).atZone(zone)
            prefs.write(StatsMath.apply(current, event, time.toLocalDate().toEpochDay(), time.hour, now))
        }

    suspend fun recordArticleRead(words: Int, minutes: Int) = store.edit { prefs ->
        if (prefs[Keys.ENABLED] == false) return@edit
        prefs[Keys.READER_ARTICLES] = (prefs[Keys.READER_ARTICLES] ?: 0) + 1
        prefs[Keys.READER_WORDS] = (prefs[Keys.READER_WORDS] ?: 0L) + words
        prefs[Keys.READER_MINUTES] = (prefs[Keys.READER_MINUTES] ?: 0) + minutes
    }

    suspend fun setEnabled(value: Boolean) = store.edit { prefs ->
        if (!value) prefs.clearCounters()
        prefs[Keys.ENABLED] = value
    }

    suspend fun reset() = store.edit { it.clearCounters() }

    /** Nur für Debug-Builds: füllt plausible Beispieldaten für Screenshots und UI-Tests. */
    suspend fun seedDemo(today: LocalDate = LocalDate.now()) = store.edit { prefs ->
        val random = Random(42)
        val hosts = listOf(
            "example.org",
            "news.example.com",
            "tech.example.net",
            "blog.example.org",
            "archive.example.com",
            "science.example.org",
            "culture.example.net",
            "docs.example.com",
        )
        val sources = listOf("share", "share", "share", "process_text", "clipboard", "history")
        val hourWeights = intArrayOf(0, 0, 0, 0, 0, 1, 3, 6, 5, 3, 2, 2, 4, 3, 2, 2, 2, 3, 4, 5, 6, 8, 7, 3)
        val hourPool = hourWeights.flatMapIndexed { hour, w -> List(w) { hour } }
        var stats = UnlockStats()
        for (offset in 150 downTo 0) {
            val day = today.minusDays(offset.toLong())
            val weekendBoost = if (day.dayOfWeek.value >= 6) 2 else 0
            val count = (random.nextInt(-2, 5) + weekendBoost).coerceAtLeast(0)
            repeat(count) {
                val event = UnlockEvent(
                    source = sources.random(random),
                    service = if (random.nextInt(6) == 0) "wayback" else "archive_today",
                    host = hosts[(random.nextDouble() * random.nextDouble() * hosts.size).toInt()],
                    trackersRemoved = random.nextInt(0, 4),
                )
                stats = StatsMath.apply(stats, event, day.toEpochDay(), hourPool.random(random), 0L)
            }
        }
        val first = today.minusDays(150).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        stats = stats.copy(
            firstUnlockAt = first,
            readerArticles = stats.total * 3 / 4,
            readerWords = stats.total * 3 / 4 * 1_150L,
            readerMinutes = stats.total * 3 / 4 * 5,
        )
        prefs.write(stats)
    }

    private fun MutablePreferences.clearCounters() {
        listOf(
            Keys.TOTAL, Keys.FIRST, Keys.DAILY, Keys.HOURLY, Keys.SOURCES, Keys.SERVICES, Keys.HOSTS,
            Keys.TRACKERS, Keys.READER_ARTICLES, Keys.READER_WORDS, Keys.READER_MINUTES,
        ).forEach { remove(it) }
    }

    private fun MutablePreferences.write(stats: UnlockStats) {
        this[Keys.TOTAL] = stats.total
        stats.firstUnlockAt?.let { this[Keys.FIRST] = it }
        this[Keys.DAILY] = StatsMath.encodeDaily(stats.daily)
        this[Keys.HOURLY] = StatsMath.encodeHourly(stats.hourly)
        this[Keys.SOURCES] = StatsMath.encodeCounts(stats.sources)
        this[Keys.SERVICES] = StatsMath.encodeCounts(stats.services)
        this[Keys.HOSTS] = StatsMath.encodeCounts(stats.hosts)
        this[Keys.TRACKERS] = stats.trackersRemoved
        this[Keys.READER_ARTICLES] = stats.readerArticles
        this[Keys.READER_WORDS] = stats.readerWords
        this[Keys.READER_MINUTES] = stats.readerMinutes
    }

    private fun Preferences.toStats() = UnlockStats(
        enabled = this[Keys.ENABLED] ?: true,
        total = this[Keys.TOTAL] ?: 0,
        firstUnlockAt = this[Keys.FIRST],
        daily = StatsMath.decodeDaily(this[Keys.DAILY]),
        hourly = StatsMath.decodeHourly(this[Keys.HOURLY]),
        sources = StatsMath.decodeCounts(this[Keys.SOURCES]),
        services = StatsMath.decodeCounts(this[Keys.SERVICES]),
        hosts = StatsMath.decodeCounts(this[Keys.HOSTS]),
        trackersRemoved = this[Keys.TRACKERS] ?: 0,
        readerArticles = this[Keys.READER_ARTICLES] ?: 0,
        readerWords = this[Keys.READER_WORDS] ?: 0L,
        readerMinutes = this[Keys.READER_MINUTES] ?: 0,
    )

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val TOTAL = intPreferencesKey("total")
        val FIRST = longPreferencesKey("first_unlock_at")
        val DAILY = stringPreferencesKey("daily")
        val HOURLY = stringPreferencesKey("hourly")
        val SOURCES = stringPreferencesKey("sources")
        val SERVICES = stringPreferencesKey("services")
        val HOSTS = stringPreferencesKey("hosts")
        val TRACKERS = intPreferencesKey("trackers_removed")
        val READER_ARTICLES = intPreferencesKey("reader_articles")
        val READER_WORDS = longPreferencesKey("reader_words")
        val READER_MINUTES = intPreferencesKey("reader_minutes")
    }
}
