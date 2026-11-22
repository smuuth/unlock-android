package dev.unlock.share

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlCleanerTest {

    private val amp = UrlCleaner.Options(normalizeAmp = true)

    @Test fun removesUtmAndClickIds() = assertEquals(
        "https://example.com/a?id=5",
        UrlCleaner.clean("https://example.com/a?utm_source=x&id=5&utm_medium=y&fbclid=abc&gclid=1"),
    )

    @Test fun removesWholeQueryIfOnlyTracking() = assertEquals(
        "https://example.com/a",
        UrlCleaner.clean("https://example.com/a?utm_campaign=c&mc_cid=1&mc_eid=2&igshid=3&ref=4&si=5"),
    )

    @Test fun trackingNamesAreCaseInsensitive() =
        assertEquals("https://example.com/", UrlCleaner.clean("https://example.com/?UTM_Source=x&FBCLID=y"))

    @Test fun keepsFragmentAndOrder() = assertEquals(
        "https://example.com/a?b=2&a=1#section",
        UrlCleaner.clean("https://example.com/a?b=2&utm_term=x&a=1#section"),
    )

    @Test fun keepsEncodedValues() = assertEquals(
        "https://example.com/search?q=a%20b%26c",
        UrlCleaner.clean("https://example.com/search?q=a%20b%26c&utm_source=x"),
    )

    @Test fun trackingRemovalCanBeDisabled() {
        val url = "https://example.com/a?utm_source=x"
        assertEquals(url, UrlCleaner.clean(url, UrlCleaner.Options(removeTracking = false)))
    }

    @Test fun resolvesGoogleRedirect() = assertEquals(
        "https://example.com/story?id=1",
        UrlCleaner.clean("https://www.google.com/url?sa=t&q=https%3A%2F%2Fexample.com%2Fstory%3Fid%3D1%26utm_source%3Dg&ved=x"),
    )

    @Test fun resolvesGoogleRedirectWithUrlParamAndCountryTld() = assertEquals(
        "https://example.de/a",
        UrlCleaner.clean("https://www.google.co.uk/url?url=https://example.de/a&rct=j"),
    )

    @Test fun resolvesNestedRedirects() = assertEquals(
        "https://example.com/x",
        UrlCleaner.clean(
            "https://www.google.com/url?q=" +
                java.net.URLEncoder.encode("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fx%3Ffbclid%3D1", "UTF-8"),
        ),
    )

    @Test fun ignoresNonHttpRedirectTargets() {
        val url = "https://www.google.com/url?q=javascript:alert(1)"
        assertEquals(url, UrlCleaner.clean(url))
    }

    @Test fun unwrapsGoogleAmpCache() = assertEquals(
        "https://example.com/news/article",
        UrlCleaner.clean("https://www.google.com/amp/s/example.com/news/article"),
    )

    @Test fun unwrapsAmpProjectCache() = assertEquals(
        "https://www.example.com/a/amp/",
        UrlCleaner.clean("https://www-example-com.cdn.ampproject.org/c/s/www.example.com/a/amp/"),
    )

    @Test fun normalizesAmpPathsAndSubdomains() {
        assertEquals("https://example.com/a/", UrlCleaner.clean("https://example.com/a/amp/", amp))
        assertEquals("https://example.com/news/x", UrlCleaner.clean("https://example.com/amp/news/x", amp))
        assertEquals("https://example.com/story.html", UrlCleaner.clean("https://example.com/story.amp.html", amp))
        assertEquals("https://example.com/a", UrlCleaner.clean("https://amp.example.com/a", amp))
        assertEquals("https://example.com/a", UrlCleaner.clean("https://m.example.com/a?amp=1", amp))
        assertEquals("https://example.co.uk/a", UrlCleaner.clean("https://m.example.co.uk/a", amp))
    }

    @Test fun doesNotStripTwoLabelHosts() = assertEquals("https://m.me/x", UrlCleaner.clean("https://m.me/x", amp))

    @Test fun ampNormalizationIsOptional() {
        val url = "https://m.example.com/a/amp"
        assertEquals(url, UrlCleaner.clean(url))
    }

    @Test fun keepsPortAndUserInfo() = assertEquals(
        "http://user@example.com:8080/a?x=1",
        UrlCleaner.clean("http://user@example.com:8080/a?x=1&utm_source=y"),
    )

    @Test fun leavesUnparsableInputAlone() = assertEquals("not a url", UrlCleaner.clean("not a url"))
}
