package dev.unlock.archive

import java.net.URLEncoder

/** Baut aus einer (bereinigten) URL die Adresse beim Archivdienst. Reines Kotlin, ohne Android. */
object TargetUrlBuilder {

    fun build(url: String, target: ArchiveTarget, domain: ArchiveDomain = ArchiveDomain.DEFAULT): String {
        // Fragmente (#…) sind rein clientseitig und würden beim Anhängen als Pfad
        // die restliche URL für den Archivdienst abschneiden.
        val withoutFragment = url.substringBefore('#')
        return target.template
            .replace("{domain}", domain.host)
            .replace("{encodedUrl}", URLEncoder.encode(withoutFragment, Charsets.UTF_8.name()))
            .replace("{url}", escapeForPath(withoutFragment))
    }

    /**
     * Archivdienste erwarten die Original-URL lesbar im Pfad (`/newest/https://…`).
     * Wir escapen daher nur Zeichen, die in einer URL nicht roh stehen dürfen
     * (Leerzeichen, Steuerzeichen, Nicht-ASCII, `"<>\^`{|}`). Bereits vorhandene
     * Prozent-Escapes bleiben unverändert.
     */
    internal fun escapeForPath(url: String): String {
        val sb = StringBuilder(url.length + 16)
        for (byte in url.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            if (c in 0x21..0x7E && c.toChar() !in UNSAFE) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
            }
        }
        return sb.toString()
    }

    private const val UNSAFE = "\"<>\\^`{|}"
    private val HEX = "0123456789ABCDEF".toCharArray()
}
