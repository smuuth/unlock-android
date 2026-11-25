package dev.unlock.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import dev.unlock.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Was die Oberfläche zum Thema Updates anzeigen soll. */
data class UpdateState(
    /** Neuere Version auf GitHub, sonst `null`. */
    val available: ReleaseInfo? = null,
    val phase: Phase = Phase.IDLE,
    /** Download-Fortschritt 0..1, `null` wenn unbekannt. */
    val progress: Float? = null,
    val lastCheckedAt: Long = 0L,
    /** Ergebnis der letzten *manuellen* Prüfung (für den Knopf in den Einstellungen). */
    val manualResult: ManualResult? = null,
    val error: UpdateError? = null,
    /** Der Nutzer muss „Unbekannte Apps installieren“ für Unlock erlauben. */
    val needsInstallPermission: Boolean = false,
) {
    enum class Phase { IDLE, CHECKING, DOWNLOADING, INSTALLING }
    enum class ManualResult { UP_TO_DATE, AVAILABLE, FAILED }
    enum class UpdateError { DOWNLOAD_FAILED, INVALID_APK, INSTALL_FAILED }

    val busy: Boolean get() = phase != Phase.IDLE
}

/**
 * Selbst-Update über GitHub Releases (kein Play Store).
 *
 * - Fragt beim App-Start höchstens etwa einmal täglich `releases/latest` ab; offline, bei Rate-Limits
 *   oder sonstigen Fehlern passiert lautlos nichts.
 * - Lädt das APK in den App-Cache, prüft Paketnamen, Versionsnummer und Signatur gegen die installierte
 *   App und übergibt es dann per [PackageInstaller] an den System-Installer.
 * - Übermittelt außer der üblichen HTTP-Anfrage an GitHub nichts.
 */
class UpdateManager(
    context: Context,
    private val scope: CoroutineScope,
    private val installedVersion: String = BuildConfig.VERSION_NAME,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
    private val onTrackEvent: (String, Map<String, String>) -> Unit = { _, _ -> },
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UpdateState(available = loadCachedRelease(), lastCheckedAt = prefs.getLong(KEY_LAST_CHECK, 0L)))
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkJob: Job? = null

    /** Beim App-Start aufrufen – prüft nur, wenn die letzte Prüfung lange genug her ist. */
    fun checkIfDue(now: Long = System.currentTimeMillis()) {
        if (!UpdateSchedule.isDue(_state.value.lastCheckedAt, now)) return
        check(manual = false)
    }

    /** Knopf „Nach Updates suchen“. */
    fun checkNow() = check(manual = true)

    private fun check(manual: Boolean) {
        if (checkJob?.isActive == true || _state.value.phase == UpdateState.Phase.DOWNLOADING ||
            _state.value.phase == UpdateState.Phase.INSTALLING
        ) return
        if (manual) _state.update { it.copy(phase = UpdateState.Phase.CHECKING, manualResult = null, error = null) }
        checkJob = scope.launch(Dispatchers.IO) {
            val result = runCatching { fetchLatestRelease() }
            val now = System.currentTimeMillis()
            result.onSuccess { release ->
                val newer = release?.takeIf { AppVersion.isNewer(it.version, installedVersion) }
                saveCheck(now, newer)
                if (newer != null) {
                    onTrackEvent(
                        "Unlock.Update.available",
                        mapOf("targetVersion" to newer.version, "manual" to manual.toString()),
                    )
                }
                if (manual) {
                    onTrackEvent(
                        "Unlock.Update.checked",
                        mapOf("result" to if (newer != null) "available" else "up_to_date"),
                    )
                }
                _state.update {
                    it.copy(
                        available = newer,
                        lastCheckedAt = now,
                        phase = if (it.phase == UpdateState.Phase.CHECKING) UpdateState.Phase.IDLE else it.phase,
                        manualResult = if (manual) {
                            if (newer != null) UpdateState.ManualResult.AVAILABLE else UpdateState.ManualResult.UP_TO_DATE
                        } else {
                            it.manualResult
                        },
                    )
                }
            }.onFailure {
                if (manual) {
                    onTrackEvent("Unlock.Update.checked", mapOf("result" to "failed"))
                }
                // Offline/Rate-Limit/Fehler: lautlos. Automatisch wird erst beim nächsten Start erneut geprüft.
                _state.update {
                    it.copy(
                        phase = if (it.phase == UpdateState.Phase.CHECKING) UpdateState.Phase.IDLE else it.phase,
                        manualResult = if (manual) UpdateState.ManualResult.FAILED else it.manualResult,
                    )
                }
            }
        }
    }

    /** `null` wenn es (noch) kein passendes Release gibt; wirft bei Netzwerk-/Serverfehlern. */
    private fun fetchLatestRelease(): ReleaseInfo? {
        val conn = (URL(latestReleaseUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Unlock-Android/$installedVersion")
        }
        try {
            return when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> ReleaseParser.parse(conn.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null // noch kein Release veröffentlicht
                else -> throw IOException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Darf Unlock Apps installieren? (Einstellung „Unbekannte Apps installieren“) */
    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    /** Öffnet direkt die Systemeinstellung „Unbekannte Apps installieren“ für Unlock. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))

    fun dismissPermissionHint() = _state.update { it.copy(needsInstallPermission = false) }

    /**
     * Nach der Rückkehr aus den Einstellungen aufrufen: Wurde die Erlaubnis gerade erteilt, geht es direkt weiter.
     */
    fun onResume() {
        if (_state.value.needsInstallPermission && canInstall()) {
            _state.update { it.copy(needsInstallPermission = false) }
            startUpdate()
        }
    }

    /** Tipp auf „Jetzt installieren“. */
    fun startUpdate() {
        val release = _state.value.available ?: return
        if (_state.value.phase == UpdateState.Phase.DOWNLOADING || _state.value.phase == UpdateState.Phase.INSTALLING) return
        if (!canInstall()) {
            onTrackEvent("Unlock.Update.permissionNeeded", mapOf("targetVersion" to release.version))
            _state.update { it.copy(needsInstallPermission = true) }
            return
        }
        onTrackEvent("Unlock.Update.started", mapOf("targetVersion" to release.version))
        _state.update { it.copy(phase = UpdateState.Phase.DOWNLOADING, progress = 0f, error = null, needsInstallPermission = false) }
        scope.launch(Dispatchers.IO) {
            val apk = runCatching { download(release) }.getOrNull()
            if (apk == null) {
                fail(UpdateState.UpdateError.DOWNLOAD_FAILED)
                return@launch
            }
            if (!isValidUpdate(apk)) {
                apk.delete()
                fail(UpdateState.UpdateError.INVALID_APK)
                return@launch
            }
            _state.update { it.copy(phase = UpdateState.Phase.INSTALLING, progress = null) }
            runCatching { install(apk) }.onFailure { fail(UpdateState.UpdateError.INSTALL_FAILED) }
        }
    }

    private fun fail(error: UpdateState.UpdateError) {
        onTrackEvent("Unlock.Update.failed", mapOf("error" to error.name.lowercase()))
        _state.update { it.copy(phase = UpdateState.Phase.IDLE, progress = null, error = error) }
    }

    private fun download(release: ReleaseInfo): File {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // alte Downloads aufräumen
        val target = File(dir, "Unlock-${release.version.replace(Regex("[^0-9A-Za-z._-]"), "_")}.apk")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // GitHub leitet auf objects.githubusercontent.com um
            setRequestProperty("User-Agent", "Unlock-Android/$installedVersion")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastReported) {
                                lastReported = percent
                                _state.update { it.copy(progress = percent / 100f) }
                            }
                        }
                    }
                    if (release.apkSize > 0 && done != release.apkSize) throw IOException("incomplete download")
                }
            }
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Gleiches Paket, höhere Versionsnummer und vom selben Schlüssel signiert wie die installierte App. */
    @Suppress("DEPRECATION")
    private fun isValidUpdate(apk: File): Boolean = runCatching {
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        val installed = pm.getPackageInfo(app.packageName, flags)
        if (archive.packageName != app.packageName) return false
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) return false
        val archiveCerts = signerDigests(archive)
        // Kann das System die Signatur des Archivs nicht auslesen, entscheidet der Installer selbst.
        archiveCerts.isEmpty() || archiveCerts.any { it in signerDigests(installed) }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()
        return signatures.mapNotNull { sig ->
            sig?.toByteArray()?.let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            }
        }.toSet()
    }

    private fun install(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("Unlock.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val callback = Intent(app, UpdateInstallReceiver::class.java).setPackage(app.packageName)
                val pending = PendingIntent.getBroadcast(
                    app,
                    sessionId,
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        } finally {
            apk.delete() // Inhalt liegt jetzt in der Installer-Session
        }
    }

    /** Rückmeldung des System-Installers (siehe [UpdateInstallReceiver]). */
    internal fun onInstallStatus(status: Int) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit // Bestätigungsdialog wird gerade angezeigt
            PackageInstaller.STATUS_SUCCESS -> {
                onTrackEvent("Unlock.Update.completed", emptyMap())
                _state.update { it.copy(phase = UpdateState.Phase.IDLE, available = null) }
            }
            // Abgebrochen: Hinweis einfach stehen lassen, ohne Fehlermeldung.
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                onTrackEvent("Unlock.Update.aborted", emptyMap())
                _state.update { it.copy(phase = UpdateState.Phase.IDLE, progress = null) }
            }
            else -> fail(UpdateState.UpdateError.INSTALL_FAILED)
        }
    }

    private fun loadCachedRelease(): ReleaseInfo? {
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        val url = prefs.getString(KEY_URL, null) ?: return null
        // Inzwischen (z. B. manuell) aktualisiert? Dann gibt es nichts mehr anzuzeigen.
        if (!AppVersion.isNewer(version, installedVersion)) return null
        return ReleaseInfo(
            version = version,
            tag = prefs.getString(KEY_TAG, null) ?: "v$version",
            apkUrl = url,
            apkSize = prefs.getLong(KEY_SIZE, 0L),
        )
    }

    private fun saveCheck(now: Long, release: ReleaseInfo?) = prefs.edit {
        putLong(KEY_LAST_CHECK, now)
        if (release == null) {
            remove(KEY_VERSION); remove(KEY_TAG); remove(KEY_URL); remove(KEY_SIZE)
        } else {
            putString(KEY_VERSION, release.version)
            putString(KEY_TAG, release.tag)
            putString(KEY_URL, release.apkUrl)
            putLong(KEY_SIZE, release.apkSize)
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/smuuth/unlock-android/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/smuuth/unlock-android/releases/latest"

        private const val PREFS = "updates"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_VERSION = "available_version"
        private const val KEY_TAG = "available_tag"
        private const val KEY_URL = "available_url"
        private const val KEY_SIZE = "available_size"
    }
}
