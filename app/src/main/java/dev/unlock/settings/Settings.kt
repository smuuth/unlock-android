package dev.unlock.settings

import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveTarget

data class Settings(
    val target: ArchiveTarget = ArchiveTarget.DEFAULT,
    val domain: ArchiveDomain = ArchiveDomain.DEFAULT,
    val cleanUrls: Boolean = true,
    val normalizeAmp: Boolean = false,
    val useCustomTabs: Boolean = true,
    /** Aus Datenschutzgründen standardmäßig aus. */
    val historyEnabled: Boolean = false,
)

data class HistoryEntry(val url: String, val timestamp: Long)
