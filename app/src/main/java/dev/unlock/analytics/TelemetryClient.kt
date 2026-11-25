package dev.unlock.analytics

import android.content.Context
import dev.unlock.BuildConfig
import dev.unlock.settings.Settings
import dev.unlock.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Schlanker, anonymer TelemetryDeck-Client (Ingest API v2) ohne Drittanbieter-SDK.
 *
 * - Sendet niemals Artikel-URLs, gelesene Domains, Titel oder personenbezogene Daten.
 * - Nutzt eine lokal generierte, SHA-256-gehashte Zufalls-ID (wird beim Opt-out sofort gelöscht).
 * - Reichert Signale automatisch mit standardisierten TelemetryDeck-Umgebungs-, Sitzungs-,
 *   Akquisitions-, Retention- und Kalenderparametern an.
 * - Macht ohne konfigurierte `TELEMETRY_APP_ID` oder bei deaktivierter Option gar nichts.
 * - Läuft fire-and-forget im Hintergrund und schlägt offline lautlos fehl.
 */
class TelemetryClient(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val appId: String = BuildConfig.TELEMETRY_APP_ID,
    private val isTestMode: Boolean = BuildConfig.DEBUG,
    private val sessionStore: TelemetrySessionStore = TelemetrySessionStore(context),
    private val environmentProvider: (ZonedDateTime) -> Map<String, String> = { zdt ->
        TelemetryEnvironment.collect(context, isTestMode, zdt)
    },
    private val sender: (endpoint: String, jsonBody: String) -> Unit = ::postJson,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {
    private val sessionLock = Any()
    private val launchTracked = AtomicBoolean(false)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var lastNavigationPath: String = ""

    /** `Unlock.App.launched` – höchstens einmal pro Prozess, gleiche Opt-out-Regeln wie [track]. */
    fun trackLaunch() {
        if (!launchTracked.compareAndSet(false, true)) return
        track(SIGNAL_LAUNCHED)
    }

    fun track(
        type: String,
        params: Map<String, String> = emptyMap(),
        floatValue: Double? = null,
    ) {
        if (appId.isBlank()) return

        scope.launch(ioDispatcher) {
            runCatching {
                val settings = settingsRepository.current()
                if (!settings.analyticsEnabled) return@launch

                val rawClientId = settingsRepository.getOrCreateAnalyticsClientId()
                val hashedUser = sha256Hex(rawClientId)
                val nowMs = clock()
                val zdt = Instant.ofEpochMilli(nowMs).atZone(zoneProvider())
                val receivedAt = Instant.ofEpochMilli(nowMs).toString()

                val session = synchronized(sessionLock) {
                    sessionStore.touch(nowMs, zdt.toLocalDate())
                }
                val baseParams = environmentProvider(zdt) + session.retentionParams

                val signals = buildList {
                    if (session.isNewInstall) {
                        add(SignalPayload(SIGNAL_NEW_INSTALL, baseParams))
                    }
                    if (session.isNewSession) {
                        add(SignalPayload(SIGNAL_SESSION_STARTED, baseParams + settingsSnapshotParams(settings)))
                    }
                    add(SignalPayload(type, baseParams + params, floatValue))
                }

                val body = buildBatchPayloadJson(
                    appId = appId,
                    clientUser = hashedUser,
                    sessionId = session.sessionId,
                    signals = signals,
                    isTestMode = isTestMode,
                    receivedAt = receivedAt,
                )
                sender(INGEST_URL, body)
            }
        }
    }

    /** Sendet ein standardisiertes `TelemetryDeck.Navigation.pathChanged`-Signal bei Bildschirmwechseln. */
    fun trackNavigation(destinationPath: String) {
        if (appId.isBlank()) return
        val source = synchronized(sessionLock) {
            val prev = lastNavigationPath
            if (prev == destinationPath) return
            lastNavigationPath = destinationPath
            prev
        }
        track(
            SIGNAL_NAVIGATION,
            mapOf(
                "TelemetryDeck.Navigation.schemaVersion" to "1",
                "TelemetryDeck.Navigation.sourcePath" to source,
                "TelemetryDeck.Navigation.destinationPath" to destinationPath,
                "TelemetryDeck.Navigation.identifier" to "$source -> $destinationPath",
            ),
        )
    }

    /** Löscht beim Opt-out alle lokal gespeicherten Sitzungs- und Retention-Zähler sofort. */
    fun clearLocalState() {
        synchronized(sessionLock) {
            sessionStore.clear()
            lastNavigationPath = ""
        }
    }

    internal data class SignalPayload(
        val type: String,
        val params: Map<String, String>,
        val floatValue: Double? = null,
    )

    companion object {
        private const val INGEST_URL = "https://nom.telemetrydeck.com/v2/"
        const val SIGNAL_LAUNCHED = "Unlock.App.launched"
        const val SIGNAL_SESSION_STARTED = "TelemetryDeck.Session.started"
        const val SIGNAL_NEW_INSTALL = "TelemetryDeck.Acquisition.newInstallDetected"
        const val SIGNAL_NAVIGATION = "TelemetryDeck.Navigation.pathChanged"

        private fun postJson(endpoint: String, jsonBody: String) {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 5_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }

        internal fun settingsSnapshotParams(settings: Settings): Map<String, String> = mapOf(
            "Unlock.Settings.openMode" to settings.openMode.id,
            "Unlock.Settings.service" to settings.target.service.name.lowercase(),
            "Unlock.Settings.target" to settings.target.id,
            "Unlock.Settings.domain" to settings.domain.host,
            "Unlock.Settings.cleanUrls" to settings.cleanUrls.toString(),
            "Unlock.Settings.normalizeAmp" to settings.normalizeAmp.toString(),
            "Unlock.Settings.historyEnabled" to settings.historyEnabled.toString(),
            "Unlock.Settings.readerTheme" to settings.readerTheme.id,
            "Unlock.Settings.readerFont" to settings.readerFont.id,
            "Unlock.Settings.readerJustify" to settings.readerJustify.toString(),
            "Unlock.Settings.readerFontScale" to TelemetryEnvironment.formatDecimal(settings.readerFontScale),
            "Unlock.Settings.hasUnlocked" to settings.hasUnlocked.toString(),
        )

        internal fun sha256Hex(input: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        internal fun buildPayloadJson(
            appId: String,
            clientUser: String,
            sessionId: String,
            type: String,
            params: Map<String, String>,
            isTestMode: Boolean,
            floatValue: Double? = null,
            receivedAt: String? = null,
        ): String = buildBatchPayloadJson(
            appId = appId,
            clientUser = clientUser,
            sessionId = sessionId,
            signals = listOf(SignalPayload(type, params, floatValue)),
            isTestMode = isTestMode,
            receivedAt = receivedAt,
        )

        internal fun buildBatchPayloadJson(
            appId: String,
            clientUser: String,
            sessionId: String,
            signals: List<SignalPayload>,
            isTestMode: Boolean,
            receivedAt: String? = null,
        ): String {
            val array = JSONArray()
            for (item in signals) {
                val payloadObj = JSONObject()
                for ((k, v) in item.params) {
                    payloadObj.put(k, v)
                }
                val signal = JSONObject()
                    .put("appID", appId)
                    .put("clientUser", clientUser)
                    .put("sessionID", sessionId)
                    .put("type", item.type)
                    .put("isTestMode", isTestMode)
                    .put("payload", payloadObj)
                if (!receivedAt.isNullOrBlank()) {
                    signal.put("receivedAt", receivedAt)
                }
                val fv = item.floatValue
                if (fv != null && fv.isFinite()) {
                    signal.put("floatValue", fv)
                }
                array.put(signal)
            }
            return array.toString()
        }
    }
}
