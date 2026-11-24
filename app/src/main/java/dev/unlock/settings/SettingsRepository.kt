package dev.unlock.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveTarget
import dev.unlock.reader.ReaderFont
import dev.unlock.reader.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Einstellungen + optionaler Verlauf in Jetpack DataStore.
 *
 * Für den Share-Pfad hält das Repository einen In-Memory-Cache, der beim
 * Prozessstart im Hintergrund befüllt wird ([warmUp]). Nur wenn der Share-Intent
 * schneller ist als dieses erste Lesen, wird einmalig per `runBlocking` gelesen
 * (eine kleine Datei, wenige Millisekunden).
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    @Volatile
    private var cached: Settings? = null

    val settings: Flow<Settings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    val history: Flow<List<HistoryEntry>> = store.data.map { decodeHistory(it[Keys.HISTORY]) }.distinctUntilChanged()

    fun warmUp(scope: CoroutineScope) {
        settings.onEach { cached = it }.launchIn(scope)
    }

    /** Aktuelle Einstellungen für den Share-Pfad – blockiert höchstens beim allerersten Zugriff. */
    fun current(): Settings = cached ?: runBlocking { settings.first() }.also { cached = it }

    suspend fun setTarget(target: ArchiveTarget) = store.edit { it[Keys.TARGET] = target.id }
    suspend fun setDomain(domain: ArchiveDomain) = store.edit { it[Keys.DOMAIN] = domain.host }
    suspend fun setCleanUrls(value: Boolean) = store.edit { it[Keys.CLEAN] = value }
    suspend fun setNormalizeAmp(value: Boolean) = store.edit { it[Keys.AMP] = value }
    suspend fun setOpenMode(mode: OpenMode) = store.edit { it[Keys.OPEN_MODE] = mode.id }
    suspend fun markUnlocked() = store.edit { it[Keys.HAS_UNLOCKED] = true }
    suspend fun setReaderTheme(theme: ReaderTheme) = store.edit { it[Keys.READER_THEME] = theme.id }
    suspend fun setReaderFont(font: ReaderFont) = store.edit { it[Keys.READER_FONT] = font.id }
    suspend fun setReaderJustify(value: Boolean) = store.edit { it[Keys.READER_JUSTIFY] = value }
    suspend fun setReaderFontScale(scale: Float) = store.edit {
        it[Keys.READER_FONT_SCALE] = scale.coerceIn(0.85f, 1.45f)
    }

    suspend fun setHistoryEnabled(value: Boolean) = store.edit {
        it[Keys.HISTORY_ENABLED] = value
        // Beim Abschalten sofort alles verwerfen – nichts soll heimlich liegen bleiben.
        if (!value) it.remove(Keys.HISTORY)
    }

    suspend fun setAnalyticsEnabled(value: Boolean) = store.edit {
        it[Keys.ANALYTICS_ENABLED] = value
        if (!value) it.remove(Keys.ANALYTICS_CLIENT_ID)
    }

    suspend fun getOrCreateAnalyticsClientId(): String {
        var id: String? = null
        store.edit { prefs ->
            val existing = prefs[Keys.ANALYTICS_CLIENT_ID]
            if (!existing.isNullOrBlank()) {
                id = existing
            } else {
                val created = java.util.UUID.randomUUID().toString()
                prefs[Keys.ANALYTICS_CLIENT_ID] = created
                id = created
            }
        }
        return id ?: java.util.UUID.randomUUID().toString()
    }

    suspend fun addToHistory(url: String, now: Long = System.currentTimeMillis()) = store.edit { prefs ->
        if (prefs[Keys.HISTORY_ENABLED] != true) return@edit
        val entries = listOf(HistoryEntry(url, now)) + decodeHistory(prefs[Keys.HISTORY]).filter { it.url != url }
        prefs[Keys.HISTORY] = encodeHistory(entries.take(MAX_HISTORY))
    }

    suspend fun clearHistory() = store.edit { it.remove(Keys.HISTORY) }

    private fun Preferences.toSettings(): Settings {
        val openMode = OpenMode.fromId(this[Keys.OPEN_MODE]) ?: when (this[Keys.CUSTOM_TABS]) {
            false -> OpenMode.BROWSER
            else -> OpenMode.DEFAULT
        }
        return Settings(
            target = ArchiveTarget.fromId(this[Keys.TARGET]) ?: ArchiveTarget.DEFAULT,
            domain = ArchiveDomain.fromHost(this[Keys.DOMAIN]) ?: ArchiveDomain.DEFAULT,
            cleanUrls = this[Keys.CLEAN] ?: true,
            normalizeAmp = this[Keys.AMP] ?: false,
            openMode = openMode,
            historyEnabled = this[Keys.HISTORY_ENABLED] ?: false,
            analyticsEnabled = this[Keys.ANALYTICS_ENABLED] ?: true,
            readerTheme = ReaderTheme.fromId(this[Keys.READER_THEME]),
            readerFont = ReaderFont.fromId(this[Keys.READER_FONT]),
            readerFontScale = (this[Keys.READER_FONT_SCALE] ?: 1.05f).coerceIn(0.85f, 1.45f),
            readerJustify = this[Keys.READER_JUSTIFY] ?: false,
            hasUnlocked = this[Keys.HAS_UNLOCKED] ?: false,
        )
    }

    private object Keys {
        val TARGET = stringPreferencesKey("target")
        val DOMAIN = stringPreferencesKey("archive_domain")
        val CLEAN = booleanPreferencesKey("clean_urls")
        val AMP = booleanPreferencesKey("normalize_amp")
        val CUSTOM_TABS = booleanPreferencesKey("custom_tabs")
        val OPEN_MODE = stringPreferencesKey("open_mode")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT = stringPreferencesKey("reader_font")
        val READER_FONT_SCALE = floatPreferencesKey("reader_font_scale")
        val READER_JUSTIFY = booleanPreferencesKey("reader_justify")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val HISTORY = stringPreferencesKey("history")
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        val ANALYTICS_CLIENT_ID = stringPreferencesKey("analytics_client_id")
        val HAS_UNLOCKED = booleanPreferencesKey("has_unlocked")
    }

    companion object {
        const val MAX_HISTORY = 20

        // Format: eine Zeile pro Eintrag, "<timestamp>\t<url>". URLs enthalten weder Tab noch Zeilenumbruch.
        internal fun encodeHistory(entries: List<HistoryEntry>): String =
            entries.joinToString("\n") { "${it.timestamp}\t${it.url}" }

        internal fun decodeHistory(raw: String?): List<HistoryEntry> =
            raw.orEmpty().lineSequence().mapNotNull { line ->
                val ts = line.substringBefore('\t').toLongOrNull() ?: return@mapNotNull null
                HistoryEntry(line.substringAfter('\t'), ts)
            }.toList()
    }
}
