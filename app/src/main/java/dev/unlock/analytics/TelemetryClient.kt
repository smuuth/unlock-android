package dev.unlock.analytics

import android.os.Build
import dev.unlock.BuildConfig
import dev.unlock.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Schlanker, anonymer TelemetryDeck-Client (Ingest API v2) ohne Drittanbieter-SDK.
 *
 * - Sendet niemals Artikel-URLs, gelesene Domains, Titel oder personenbezogene Daten.
 * - Nutzt eine lokal generierte, SHA-256-gehashte Zufalls-ID (wird beim Opt-out sofort gelöscht).
 * - Macht ohne konfigurierte `TELEMETRY_APP_ID` oder bei deaktivierter Option gar nichts.
 * - Läuft fire-and-forget im Hintergrund und schlägt offline lautlos fehl.
 */
class TelemetryClient(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val appId: String = BuildConfig.TELEMETRY_APP_ID,
    private val isTestMode: Boolean = BuildConfig.DEBUG,
) {
    private val sessionId: String = UUID.randomUUID().toString()

    fun track(type: String, params: Map<String, String> = emptyMap()) {
        if (appId.isBlank()) return
        if (!settingsRepository.current().analyticsEnabled) return

        scope.launch(Dispatchers.IO) {
            runCatching {
                val rawClientId = settingsRepository.getOrCreateAnalyticsClientId()
                val hashedUser = sha256Hex(rawClientId)
                val body = buildPayloadJson(
                    appId = appId,
                    clientUser = hashedUser,
                    sessionId = sessionId,
                    type = type,
                    params = defaultSystemParams() + params,
                    isTestMode = isTestMode,
                )
                postJson(INGEST_URL, body)
            }
        }
    }

    private fun defaultSystemParams(): Map<String, String> = mapOf(
        "TelemetryDeck.AppInfo.version" to BuildConfig.VERSION_NAME,
        "TelemetryDeck.AppInfo.buildNumber" to BuildConfig.VERSION_CODE.toString(),
        "TelemetryDeck.Device.systemName" to "Android",
        "TelemetryDeck.Device.systemVersion" to (Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()),
        "TelemetryDeck.RunContext.locale" to Locale.getDefault().toLanguageTag(),
    )

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

    companion object {
        private const val INGEST_URL = "https://nom.telemetrydeck.com/v2/"

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
        ): String {
            val payloadObj = JSONObject()
            for ((k, v) in params) {
                payloadObj.put(k, v)
            }
            val signal = JSONObject()
                .put("appID", appId)
                .put("clientUser", clientUser)
                .put("sessionID", sessionId)
                .put("type", type)
                .put("isTestMode", isTestMode)
                .put("payload", payloadObj)
            return JSONArray().put(signal).toString()
        }
    }
}
