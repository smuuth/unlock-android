package dev.unlock.share

import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveTarget
import dev.unlock.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkProcessorTest {

    @Test fun fullPipeline() {
        val r = LinkProcessor.process(listOf("Titel https://example.com/a?utm_source=x."), Settings())!!
        assertEquals("https://example.com/a?utm_source=x", r.originalUrl)
        assertEquals("https://example.com/a", r.cleanedUrl)
        assertEquals("https://archive.ph/newest/https://example.com/a", r.targetUrl)
    }

    @Test fun fallsBackToSubject() {
        val r = LinkProcessor.process(listOf("kein Link", "https://example.com/s"), Settings())!!
        assertEquals("https://example.com/s", r.cleanedUrl)
    }

    @Test fun respectsSettingsAndOverride() {
        val s = Settings(target = ArchiveTarget.ARCHIVE_TODAY_ALL, domain = ArchiveDomain.MD, cleanUrls = false)
        assertEquals(
            "https://archive.md/https://e.com/?utm_source=x",
            LinkProcessor.process(listOf("https://e.com/?utm_source=x"), s)!!.targetUrl,
        )
        assertEquals(
            "https://web.archive.org/web/https://e.com/?utm_source=x",
            LinkProcessor.process(listOf("https://e.com/?utm_source=x"), s, ArchiveTarget.WAYBACK)!!.targetUrl,
        )
    }

    @Test fun noUrl() = assertNull(LinkProcessor.process(listOf(null, "nichts"), Settings()))
}
