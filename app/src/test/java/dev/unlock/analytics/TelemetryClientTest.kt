package dev.unlock.analytics

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TelemetryClientTest {

    @Test fun sha256Produces64CharHex() {
        val hash = TelemetryClient.sha256Hex("test-install-id")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(hash, TelemetryClient.sha256Hex("test-install-id"))
    }

    @Test fun buildsValidTelemetryDeckV2SignalArray() {
        val json = TelemetryClient.buildPayloadJson(
            appId = "11111111-2222-3333-4444-555555555555",
            clientUser = "abc123",
            sessionId = "sess-1",
            type = "Unlock.Article.opened",
            params = mapOf("openMode" to "reader", "service" to "archive.today"),
            isTestMode = false,
        )
        val arr = JSONArray(json)
        assertEquals(1, arr.length())
        val signal = arr.getJSONObject(0)
        assertEquals("11111111-2222-3333-4444-555555555555", signal.getString("appID"))
        assertEquals("abc123", signal.getString("clientUser"))
        assertEquals("sess-1", signal.getString("sessionID"))
        assertEquals("Unlock.Article.opened", signal.getString("type"))
        assertFalse(signal.getBoolean("isTestMode"))
        val payload = signal.getJSONObject("payload")
        assertEquals("reader", payload.getString("openMode"))
        assertEquals("archive.today", payload.getString("service"))
    }
}
