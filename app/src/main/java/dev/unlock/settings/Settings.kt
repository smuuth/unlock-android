package dev.unlock.settings

import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveTarget
import dev.unlock.reader.ReaderFont
import dev.unlock.reader.ReaderTheme

enum class OpenMode(val id: String) {
    READER("reader"),
    CUSTOM_TAB("custom_tab"),
    BROWSER("browser"),
    ;

    companion object {
        val DEFAULT = READER
        fun fromId(id: String?): OpenMode? = entries.firstOrNull { it.id == id }
    }
}

data class Settings(
    val target: ArchiveTarget = ArchiveTarget.DEFAULT,
    val domain: ArchiveDomain = ArchiveDomain.DEFAULT,
    val cleanUrls: Boolean = true,
    val normalizeAmp: Boolean = false,
    val openMode: OpenMode = OpenMode.DEFAULT,
    /** Aus Datenschutzgründen standardmäßig aus. */
    val historyEnabled: Boolean = false,
    /** Rein anonyme Nutzungsstatistik ohne URLs/Inhalte (Opt-out jederzeit möglich). */
    val analyticsEnabled: Boolean = true,
    val readerTheme: ReaderTheme = ReaderTheme.DEFAULT,
    val readerFont: ReaderFont = ReaderFont.DEFAULT,
    val readerFontScale: Float = 1.05f,
    /** Blocksatz statt Flattersatz in der Leseansicht (Silbentrennung ist immer aktiv). */
    val readerJustify: Boolean = false,
    /** Wurde schon einmal ein Link geöffnet? Danach zeigt die Startseite keine Anleitung mehr. */
    val hasUnlocked: Boolean = false,
)

data class HistoryEntry(val url: String, val timestamp: Long)
