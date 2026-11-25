package dev.unlock.analytics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.unlock.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TelemetryClientTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var sessionStore: TelemetrySessionStore
    private lateinit var scopeJob: Job
    private lateinit var scope: CoroutineScope
    private val sentBatches = CopyOnWriteArrayList<String>()
    private var fakeClockMs = 1_764_066_152_000L // 2025-11-25T10:22:32Z

    @Before fun setUp() {
        settingsRepo = SettingsRepository(app)
        runBlocking { settingsRepo.setAnalyticsEnabled(true) }
        sessionStore = TelemetrySessionStore(app)
        sessionStore.clear()
        scopeJob = SupervisorJob()
        scope = CoroutineScope(scopeJob + Dispatchers.IO)
        sentBatches.clear()
    }

    @After fun tearDown() {
        runBlocking {
            drainScope()
            settingsRepo.setAnalyticsEnabled(true)
        }
        sessionStore.clear()
        scopeJob.cancel()
    }

    private fun drainScope() = runBlocking {
        scopeJob.children.toList().forEach { it.join() }
    }

    private fun newClient(appId: String = TEST_APP_ID): TelemetryClient = TelemetryClient(
        context = app,
        settingsRepository = settingsRepo,
        scope = scope,
        appId = appId,
        isTestMode = false,
        sessionStore = sessionStore,
        sender = { _, json -> sentBatches += json },
        clock = { fakeClockMs },
        zoneProvider = { ZoneId.of("Europe/Berlin") },
    )

    @Test fun sha256Produces64CharHex() {
        val hash = TelemetryClient.sha256Hex("test-install-id")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(hash, TelemetryClient.sha256Hex("test-install-id"))
    }

    @Test fun buildsValidTelemetryDeckV2SignalArrayAndHandlesFloatValue() {
        val json = TelemetryClient.buildPayloadJson(
            appId = TEST_APP_ID,
            clientUser = "abc123",
            sessionId = "sess-1",
            type = "Unlock.Article.opened",
            params = mapOf("openMode" to "reader", "service" to "archive.today"),
            isTestMode = false,
            floatValue = 3.0,
            receivedAt = "2026-11-25T10:22:32Z",
        )
        val arr = JSONArray(json)
        assertEquals(1, arr.length())
        val signal = arr.getJSONObject(0)
        assertEquals(TEST_APP_ID, signal.getString("appID"))
        assertEquals("abc123", signal.getString("clientUser"))
        assertEquals("sess-1", signal.getString("sessionID"))
        assertEquals("Unlock.Article.opened", signal.getString("type"))
        assertFalse(signal.getBoolean("isTestMode"))
        assertEquals(3.0, signal.getDouble("floatValue"), 0.0001)
        assertEquals("2026-11-25T10:22:32Z", signal.getString("receivedAt"))
        val payload = signal.getJSONObject("payload")
        assertEquals("reader", payload.getString("openMode"))
        assertEquals("archive.today", payload.getString("service"))

        val nonFiniteJson = TelemetryClient.buildPayloadJson(
            appId = TEST_APP_ID,
            clientUser = "abc123",
            sessionId = "sess-1",
            type = "Unlock.Test",
            params = emptyMap(),
            isTestMode = true,
            floatValue = Double.NaN,
        )
        assertFalse(JSONArray(nonFiniteJson).getJSONObject(0).has("floatValue"))
    }

    @Test fun blankAppIdSendsNothing() {
        val client = newClient(appId = "   ")
        client.trackLaunch()
        client.track("Unlock.Article.opened")
        client.trackNavigation("/home")
        drainScope()
        assertTrue(sentBatches.isEmpty())
    }

    @Test fun optOutSendsNothing() {
        runBlocking { settingsRepo.setAnalyticsEnabled(false) }
        val client = newClient()
        client.trackLaunch()
        client.track("Unlock.Article.opened")
        drainScope()
        assertTrue(sentBatches.isEmpty())
    }

    @Test fun firstLaunchEmitsAcquisitionSessionAndLaunchedBatchAndDeduplicatesLaunch() {
        val client = newClient()
        client.trackLaunch()
        client.trackLaunch()
        drainScope()

        assertEquals(1, sentBatches.size)
        val batch = JSONArray(sentBatches[0])
        assertEquals(3, batch.length())
        assertEquals(TelemetryClient.SIGNAL_NEW_INSTALL, batch.getJSONObject(0).getString("type"))
        assertEquals(TelemetryClient.SIGNAL_SESSION_STARTED, batch.getJSONObject(1).getString("type"))
        assertEquals(TelemetryClient.SIGNAL_LAUNCHED, batch.getJSONObject(2).getString("type"))

        val sessionPayload = batch.getJSONObject(1).getJSONObject("payload")
        assertTrue(sessionPayload.has("Unlock.Settings.openMode"))
        assertTrue(sessionPayload.has("Unlock.Settings.readerTheme"))
        assertTrue(sessionPayload.has("TelemetryDeck.Device.operatingSystem"))
        assertTrue(sessionPayload.has("TelemetryDeck.Retention.distinctDaysUsed"))
        assertEquals(
            Instant.ofEpochMilli(fakeClockMs).toString(),
            batch.getJSONObject(0).getString("receivedAt"),
        )
    }

    @Test fun subsequentTrackUsesSameSessionAndIncludesFloatValue() {
        val client = newClient()
        client.trackLaunch()
        drainScope()
        val firstSessionId = JSONArray(sentBatches[0]).getJSONObject(0).getString("sessionID")

        fakeClockMs += 15_000L
        client.track("Unlock.Article.opened", mapOf("openMode" to "reader"), floatValue = 2.0)
        drainScope()

        assertEquals(2, sentBatches.size)
        val secondBatch = JSONArray(sentBatches[1])
        assertEquals(1, secondBatch.length())
        val sig = secondBatch.getJSONObject(0)
        assertEquals("Unlock.Article.opened", sig.getString("type"))
        assertEquals(firstSessionId, sig.getString("sessionID"))
        assertEquals(2.0, sig.getDouble("floatValue"), 0.0001)
        val payload = sig.getJSONObject("payload")
        assertEquals("reader", payload.getString("openMode"))
        assertTrue(payload.has("TelemetryDeck.AppInfo.version"))
        assertTrue(payload.has("TelemetryDeck.Retention.totalSessionsCount"))
    }

    @Test fun trackNavigationDeduplicatesAndTracksTransitionsAndClearResetsPath() {
        val client = newClient()
        client.trackNavigation("/home")
        client.trackNavigation("/home")
        client.trackNavigation("/settings")
        client.trackNavigation("/home")
        drainScope()

        assertEquals(3, sentBatches.size)
        val nav1 = JSONArray(sentBatches[0]).let { it.getJSONObject(it.length() - 1) }
        assertEquals(TelemetryClient.SIGNAL_NAVIGATION, nav1.getString("type"))
        nav1.getJSONObject("payload").apply {
            assertEquals("1", getString("TelemetryDeck.Navigation.schemaVersion"))
            assertEquals("", getString("TelemetryDeck.Navigation.sourcePath"))
            assertEquals("/home", getString("TelemetryDeck.Navigation.destinationPath"))
            assertEquals(" -> /home", getString("TelemetryDeck.Navigation.identifier"))
        }

        val nav2 = JSONArray(sentBatches[1]).getJSONObject(0)
        nav2.getJSONObject("payload").apply {
            assertEquals("/home", getString("TelemetryDeck.Navigation.sourcePath"))
            assertEquals("/settings", getString("TelemetryDeck.Navigation.destinationPath"))
            assertEquals("/home -> /settings", getString("TelemetryDeck.Navigation.identifier"))
        }

        val nav3 = JSONArray(sentBatches[2]).getJSONObject(0)
        nav3.getJSONObject("payload").apply {
            assertEquals("/settings", getString("TelemetryDeck.Navigation.sourcePath"))
            assertEquals("/home", getString("TelemetryDeck.Navigation.destinationPath"))
        }

        client.clearLocalState()
        client.trackNavigation("/home")
        drainScope()
        assertEquals(4, sentBatches.size)
        val afterClear = JSONArray(sentBatches[3])
        assertEquals(TelemetryClient.SIGNAL_NEW_INSTALL, afterClear.getJSONObject(0).getString("type"))
        val nav4 = afterClear.getJSONObject(2)
        assertEquals("", nav4.getJSONObject("payload").getString("TelemetryDeck.Navigation.sourcePath"))
    }

    private companion object {
        const val TEST_APP_ID = "11111111-2222-3333-4444-555555555555"
    }
}
