package dev.unlock.archive

import dev.unlock.settings.Settings
import dev.unlock.share.LinkProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveUrlsTest {

    @Test fun unwrapsArchiveTodayVariants() {
        assertEquals("https://e.com/a?x=1", ArchiveUrls.unwrap("https://archive.ph/newest/https://e.com/a?x=1"))
        assertEquals("https://e.com/a", ArchiveUrls.unwrap("https://archive.is/https://e.com/a"))
        assertEquals("https://e.com/a", ArchiveUrls.unwrap("https://archive.today/oldest/https:/e.com/a"))
        assertEquals("https://e.com/a?b=1", ArchiveUrls.unwrap("https://archive.md/submit/?url=https%3A%2F%2Fe.com%2Fa%3Fb%3D1"))
    }

    @Test fun unwrapsWayback() {
        assertEquals("https://e.com/a", ArchiveUrls.unwrap("https://web.archive.org/web/20240101123456/https://e.com/a"))
        assertEquals("https://e.com/a", ArchiveUrls.unwrap("https://web.archive.org/web/2024id_/https://e.com/a"))
        assertEquals("https://e.com/a", ArchiveUrls.unwrap("https://web.archive.org/web/https://e.com/a"))
    }

    @Test fun snapshotIdsCannotBeUnwrapped() {
        assertNull(ArchiveUrls.unwrap("https://archive.ph/AbC12"))
        assertTrue(ArchiveUrls.isArchiveUrl("https://archive.ph/AbC12"))
    }

    @Test fun normalUrlsAreUntouched() {
        assertNull(ArchiveUrls.unwrap("https://example.com/newest/https://e.com"))
        assertFalse(ArchiveUrls.isArchiveUrl("https://example.com/"))
    }

    @Test fun reSharingDoesNotNest() {
        val r = LinkProcessor.process(listOf("https://archive.ph/newest/https://e.com/a?utm_source=x"), Settings())!!
        assertEquals("https://archive.ph/newest/https://e.com/a", r.targetUrl)
    }

    @Test fun snapshotLinkIsOpenedDirectly() {
        val r = LinkProcessor.process(listOf("https://archive.ph/AbC12"), Settings())!!
        assertEquals("https://archive.ph/AbC12", r.targetUrl)
    }
}
