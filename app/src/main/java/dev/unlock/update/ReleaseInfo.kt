package dev.unlock.update

import org.json.JSONObject

/** Eine veröffentlichte Version auf GitHub Releases mit ihrem APK-Asset. */
data class ReleaseInfo(
    /** Normalisierte Version ohne führendes „v“, z. B. `1.2.0`. */
    val version: String,
    val tag: String,
    val apkUrl: String,
    /** Größe laut GitHub in Bytes, `0` wenn unbekannt. */
    val apkSize: Long,
)

/** Versionsvergleich für Tags wie `v1.2.0`, `1.10`, `1.3.0-beta.1`. */
object AppVersion {

    /** Entfernt ein führendes `v`/`V` und Leerraum. */
    fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V").trim()

    /** `true`, wenn [candidate] echt neuer als [installed] ist. Unlesbare Versionen sind nie „neuer“. */
    fun isNewer(candidate: String, installed: String): Boolean {
        if (parse(candidate) == null || parse(installed) == null) return false
        return compare(candidate, installed) > 0
    }

    /**
     * Vergleicht numerisch Teil für Teil (`1.10 > 1.9`, fehlende Teile zählen als 0, also `1.2 == 1.2.0`).
     * Eine Vorabversion (`1.3.0-beta`) ist älter als die fertige Version (`1.3.0`).
     * Nicht lesbare Versionen gelten als älter als jede lesbare.
     */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        if (pa == null || pb == null) return compareValues(pa != null, pb != null)
        val size = maxOf(pa.numbers.size, pb.numbers.size)
        for (i in 0 until size) {
            val c = pa.numbers.getOrElse(i) { 0 }.compareTo(pb.numbers.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return when {
            pa.preRelease == pb.preRelease -> 0
            pa.preRelease == null -> 1
            pb.preRelease == null -> -1
            else -> pa.preRelease.compareTo(pb.preRelease)
        }
    }

    private data class Parsed(val numbers: List<Int>, val preRelease: String?)

    private fun parse(raw: String): Parsed? {
        val withoutBuild = normalize(raw).substringBefore('+')
        val core = withoutBuild.substringBefore('-')
        val pre = withoutBuild.substringAfter('-', missingDelimiterValue = "").ifEmpty { null }
        if (core.isEmpty()) return null
        val numbers = core.split('.').map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
        return Parsed(numbers, pre)
    }
}

/** Liest die Antwort von `GET /repos/{owner}/{repo}/releases/latest`. */
object ReleaseParser {

    /** Fester Asset-Name, den der Release-Workflow hochlädt (auch für den Link „…/releases/latest/download/Unlock.apk“). */
    const val APK_ASSET_NAME = "Unlock.apk"

    /** `null` bei kaputtem JSON, Entwürfen, Vorabversionen oder wenn kein passendes APK angehängt ist. */
    fun parse(json: String): ReleaseInfo? = runCatching {
        val obj = JSONObject(json)
        if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) return null
        val tag = obj.optString("tag_name").trim()
        if (tag.isEmpty()) return null
        val version = AppVersion.normalize(tag)
        val assets = obj.optJSONArray("assets") ?: return null
        val candidates = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .filter { it.optString("browser_download_url").startsWith("https://") }
        // Bevorzugt der feste Name; ältere Releases hießen „Unlock-v1.x.y.apk“.
        val apk = candidates.firstOrNull { it.optString("name") == APK_ASSET_NAME }
            ?: candidates.firstOrNull {
                val name = it.optString("name")
                name.startsWith("Unlock", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)
            }
            ?: return null
        ReleaseInfo(
            version = version,
            tag = tag,
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.optLong("size", 0L).coerceAtLeast(0L),
        )
    }.getOrNull()
}

/** Automatische Prüfung höchstens etwa einmal am Tag. */
object UpdateSchedule {
    const val AUTO_CHECK_INTERVAL_MS: Long = 20L * 60 * 60 * 1000

    /** Fällig, wenn noch nie geprüft, das Intervall vorbei ist oder die Uhr zurückgestellt wurde. */
    fun isDue(lastCheckMs: Long, nowMs: Long, intervalMs: Long = AUTO_CHECK_INTERVAL_MS): Boolean =
        lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= intervalMs
}
