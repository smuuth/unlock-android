package dev.unlock.archive

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetUrlBuilderTest {

    private val url = "https://example.com/a b/ä?x=1&y=2"

    @Test fun archiveTodayNewest() = assertEquals(
        "https://archive.ph/newest/https://example.com/a%20b/%C3%A4?x=1&y=2",
        TargetUrlBuilder.build(url, ArchiveTarget.ARCHIVE_TODAY_NEWEST),
    )

    @Test fun archiveTodaySubmitIsFullyEncoded() = assertEquals(
        "https://archive.ph/submit/?url=https%3A%2F%2Fexample.com%2Fa+b%2F%C3%A4%3Fx%3D1%26y%3D2",
        TargetUrlBuilder.build(url, ArchiveTarget.ARCHIVE_TODAY_SUBMIT),
    )

    @Test fun archiveTodayAllSnapshots() = assertEquals(
        "https://archive.ph/https://example.com/a",
        TargetUrlBuilder.build("https://example.com/a", ArchiveTarget.ARCHIVE_TODAY_ALL),
    )

    @Test fun wayback() = assertEquals(
        "https://web.archive.org/web/https://example.com/a?x=1",
        TargetUrlBuilder.build("https://example.com/a?x=1", ArchiveTarget.WAYBACK, ArchiveDomain.IS),
    )

    @Test fun domainIsSelectable() {
        assertEquals(
            "https://archive.is/newest/https://example.com/",
            TargetUrlBuilder.build("https://example.com/", ArchiveTarget.ARCHIVE_TODAY_NEWEST, ArchiveDomain.IS),
        )
        ArchiveDomain.entries.forEach { d ->
            assertEquals(
                "https://${d.host}/submit/?url=https%3A%2F%2Fe.com",
                TargetUrlBuilder.build("https://e.com", ArchiveTarget.ARCHIVE_TODAY_SUBMIT, d),
            )
        }
    }

    @Test fun fragmentIsDropped() {
        assertEquals(
            "https://archive.ph/newest/https://example.com/a?x=1",
            TargetUrlBuilder.build("https://example.com/a?x=1#top", ArchiveTarget.ARCHIVE_TODAY_NEWEST),
        )
        assertEquals(
            "https://archive.ph/submit/?url=https%3A%2F%2Fexample.com%2Fa",
            TargetUrlBuilder.build("https://example.com/a#top", ArchiveTarget.ARCHIVE_TODAY_SUBMIT),
        )
    }

    @Test fun existingPercentEscapesAreNotDoubleEncoded() = assertEquals(
        "https://web.archive.org/web/https://example.com/a%20b?q=%7Bx%7D%7C",
        TargetUrlBuilder.build("https://example.com/a%20b?q={x}|", ArchiveTarget.WAYBACK),
    )

    @Test fun everyTargetHasUniqueId() =
        assertEquals(ArchiveTarget.entries.size, ArchiveTarget.entries.map { it.id }.toSet().size)
}
