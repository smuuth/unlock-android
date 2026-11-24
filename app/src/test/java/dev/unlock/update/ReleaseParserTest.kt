package dev.unlock.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json ist Teil von Android → Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseParserTest {

    private fun release(
        tag: String = "v1.2.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = asset("Unlock.apk"),
    ) = """
        {
          "url": "https://api.github.com/repos/smuuth/unlock-android/releases/1",
          "html_url": "https://github.com/smuuth/unlock-android/releases/tag/$tag",
          "tag_name": "$tag",
          "name": "$tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "published_at": "2026-11-24T12:00:00Z",
          "assets": [ $assets ],
          "body": "Notes"
        }
    """.trimIndent()

    private fun asset(name: String, size: Long = 1_812_345, url: String = "https://github.com/smuuth/unlock-android/releases/download/v1.2.0/$name") = """
        {
          "name": "$name",
          "content_type": "application/vnd.android.package-archive",
          "size": $size,
          "browser_download_url": "$url"
        }
    """.trimIndent()

    @Test fun parsesTypicalGitHubRelease() {
        val info = ReleaseParser.parse(release())
        assertNotNull(info)
        info!!
        assertEquals("1.2.0", info.version)
        assertEquals("v1.2.0", info.tag)
        assertEquals("https://github.com/smuuth/unlock-android/releases/download/v1.2.0/Unlock.apk", info.apkUrl)
        assertEquals(1_812_345L, info.apkSize)
    }

    @Test fun prefersStableAssetName() {
        val assets = listOf(asset("Unlock-v1.2.0.apk"), asset("checksums.txt"), asset("Unlock.apk")).joinToString(",")
        assertEquals(
            "https://github.com/smuuth/unlock-android/releases/download/v1.2.0/Unlock.apk",
            ReleaseParser.parse(release(assets = assets))!!.apkUrl,
        )
    }

    @Test fun fallsBackToVersionedApkName() {
        val info = ReleaseParser.parse(release(assets = asset("Unlock-v1.2.0.apk")))
        assertEquals("https://github.com/smuuth/unlock-android/releases/download/v1.2.0/Unlock-v1.2.0.apk", info!!.apkUrl)
    }

    @Test fun ignoresReleaseWithoutApk() {
        assertNull(ReleaseParser.parse(release(assets = asset("checksums.txt"))))
        assertNull(ReleaseParser.parse(release(assets = "")))
    }

    @Test fun ignoresInsecureDownloadUrl() {
        assertNull(ReleaseParser.parse(release(assets = asset("Unlock.apk", url = "http://example.com/Unlock.apk"))))
    }

    @Test fun ignoresDraftsAndPreReleases() {
        assertNull(ReleaseParser.parse(release(draft = true)))
        assertNull(ReleaseParser.parse(release(prerelease = true)))
    }

    @Test fun ignoresMissingTag() {
        assertNull(ReleaseParser.parse(release(tag = "")))
    }

    @Test fun malformedJsonIsNull() {
        assertNull(ReleaseParser.parse(""))
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("""{"message":"API rate limit exceeded"}"""))
    }

    @Test fun missingSizeIsZero() {
        val noSize = """{"tag_name":"v1.3.0","assets":[{"name":"Unlock.apk","browser_download_url":"https://x.test/Unlock.apk"}]}"""
        assertEquals(0L, ReleaseParser.parse(noSize)!!.apkSize)
    }
}
