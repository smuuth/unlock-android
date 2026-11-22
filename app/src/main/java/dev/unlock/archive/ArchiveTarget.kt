package dev.unlock.archive

/** Übergeordneter Archivdienst – gruppiert die Ziele in der Oberfläche. */
enum class ArchiveService(val displayName: String) {
    ARCHIVE_TODAY("archive.today"),
    WAYBACK("Wayback Machine"),
}

/**
 * Ein konkretes Ziel (Dienst + Modus) mit URL-Template.
 *
 * Platzhalter:
 * - `{domain}`     – die gewählte archive.today-Domain (nur bei [usesArchiveDomain])
 * - `{url}`        – die Original-URL, nur minimal escaped, als Pfad angehängt
 * - `{encodedUrl}` – die Original-URL, vollständig URL-encoded (Query-Parameter)
 *
 * Neue Dienste werden einfach als weiterer Eintrag ergänzt.
 */
enum class ArchiveTarget(
    val id: String,
    val service: ArchiveService,
    val template: String,
) {
    ARCHIVE_TODAY_NEWEST("newest", ArchiveService.ARCHIVE_TODAY, "https://{domain}/newest/{url}"),
    ARCHIVE_TODAY_SUBMIT("submit", ArchiveService.ARCHIVE_TODAY, "https://{domain}/submit/?url={encodedUrl}"),
    ARCHIVE_TODAY_ALL("all", ArchiveService.ARCHIVE_TODAY, "https://{domain}/{url}"),
    WAYBACK("wayback", ArchiveService.WAYBACK, "https://web.archive.org/web/{url}"),
    ;

    val usesArchiveDomain: Boolean get() = "{domain}" in template

    companion object {
        val DEFAULT = ARCHIVE_TODAY_NEWEST

        fun fromId(id: String?): ArchiveTarget? = entries.firstOrNull { it.id == id }

        fun forService(service: ArchiveService): List<ArchiveTarget> = entries.filter { it.service == service }
    }
}

/** Wählbare archive.today-Spiegel – einzelne Domains sind zeitweise nicht erreichbar. */
enum class ArchiveDomain(val host: String) {
    PH("archive.ph"),
    IS("archive.is"),
    TODAY("archive.today"),
    MD("archive.md"),
    LI("archive.li"),
    ;

    companion object {
        val DEFAULT = PH

        fun fromHost(host: String?): ArchiveDomain? = entries.firstOrNull { it.host == host }
    }
}
