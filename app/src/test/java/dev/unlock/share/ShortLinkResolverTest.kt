package dev.unlock.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortLinkResolverTest {

    private val article = "https://www.example.org/articles/sample-story-6870389"

    @Test fun detectsGoogleShortLinks() {
        assertTrue(ShortLinkResolver.isShortLink("https://share.google/2WOs2ZxdTitqZS55J"))
        assertTrue(ShortLinkResolver.isShortLink("https://search.app/AbC123"))
        assertFalse(ShortLinkResolver.isShortLink("https://share.google/"))
        assertFalse(ShortLinkResolver.isShortLink("https://example.com/share.google"))
        assertFalse(ShortLinkResolver.isShortLink(article))
    }

    @Test fun followsGoogleRedirectChain() {
        val chain = mapOf(
            "https://share.google/2WOs2ZxdTitqZS55J" to "https://www.google.com/share.google?q=2WOs2ZxdTitqZS55J",
            "https://www.google.com/share.google?q=2WOs2ZxdTitqZS55J" to article,
        )
        val visited = mutableListOf<String>()
        val resolved = ShortLinkResolver.resolve("https://share.google/2WOs2ZxdTitqZS55J") { visited += it; chain[it] }
        assertEquals(article, resolved)
        assertEquals(2, visited.size) // Zielseite selbst wird nicht kontaktiert.
    }

    @Test fun resolvesRelativeLocation() {
        val resolved = ShortLinkResolver.resolve("https://share.google/x") {
            if (it.endsWith("/x")) "/share.google?q=x" else null
        }
        assertNull(resolved) // Kette endet auf Google ohne Ziel → Fehlschlag.
    }

    @Test fun failsWithoutRedirect() {
        assertNull(ShortLinkResolver.resolve("https://share.google/x") { null })
    }

    @Test fun nonShortLinkIsReturnedUnchanged() {
        assertEquals(article, ShortLinkResolver.resolve(article) { error("no network expected") })
    }
}
