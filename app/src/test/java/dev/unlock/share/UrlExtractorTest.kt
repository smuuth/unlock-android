package dev.unlock.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExtractorTest {

    @Test fun plainUrl() =
        assertEquals("https://example.com/a", UrlExtractor.extract("https://example.com/a"))

    @Test fun titleAndUrl() = assertEquals(
        "https://www.nytimes.com/2026/11/22/world/story.html",
        UrlExtractor.extract("Große Geschichte – hier lesen https://www.nytimes.com/2026/11/22/world/story.html"),
    )

    @Test fun urlFollowedByText() = assertEquals(
        "https://example.com/x?id=1",
        UrlExtractor.extract("Schau mal: https://example.com/x?id=1 via @feedly"),
    )

    @Test fun multipleUrlsReturnsFirst() {
        val text = "Erst https://first.example/a dann http://second.example/b"
        assertEquals("https://first.example/a", UrlExtractor.extract(text))
        assertEquals(listOf("https://first.example/a", "http://second.example/b"), UrlExtractor.extractAll(text))
    }

    @Test fun trailingPunctuationIsTrimmed() {
        assertEquals("https://example.com/a", UrlExtractor.extract("Lies das: https://example.com/a."))
        assertEquals("https://example.com/a", UrlExtractor.extract("(siehe https://example.com/a)"))
        assertEquals("https://example.com/a", UrlExtractor.extract("„https://example.com/a“!"))
        assertEquals("https://example.com/a", UrlExtractor.extract("Link: <https://example.com/a>,"))
        assertEquals("https://example.com/a", UrlExtractor.extract("https://example.com/a…"))
    }

    @Test fun balancedParenthesesAreKept() = assertEquals(
        "https://en.wikipedia.org/wiki/Mercury_(planet)",
        UrlExtractor.extract("Info (https://en.wikipedia.org/wiki/Mercury_(planet))."),
    )

    @Test fun markdownLink() = assertEquals(
        "https://example.com/post",
        UrlExtractor.extract("[Titel](https://example.com/post)"),
    )

    @Test fun schemeIsCaseInsensitive() =
        assertEquals("HTTPS://Example.com/A", UrlExtractor.extract("HTTPS://Example.com/A"))

    @Test fun bareWwwBecomesHttps() =
        assertEquals("https://www.example.com/a", UrlExtractor.extract("siehe www.example.com/a."))

    @Test fun wwwInsideUrlIsNotDuplicated() =
        assertEquals(listOf("https://www.example.com/a"), UrlExtractor.extractAll("https://www.example.com/a"))

    @Test fun noUrl() {
        assertNull(UrlExtractor.extract("Nur Text ohne Link"))
        assertNull(UrlExtractor.extract(""))
        assertNull(UrlExtractor.extract(null))
        assertNull(UrlExtractor.extract("ftp://example.com/file"))
        assertNull(UrlExtractor.extract("https://"))
    }

    @Test fun newlineSeparated() = assertEquals(
        "https://example.com/n",
        UrlExtractor.extract("Titel\nhttps://example.com/n\nGeteilt via App"),
    )
}
