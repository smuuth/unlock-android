package dev.unlock.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArticleParserTest {

    @Test fun detectsCaptchaFromDoubleQuotedJsString() {
        val raw = JSONObject.quote("""{"status":"captcha"}""")
        val res = ArticleParser.parse(raw, "https://www.spiegel.de/a", "https://archive.ph/123")
        assertEquals(ExtractionResult.NeedsInteraction(InteractionReason.CAPTCHA), res)
    }

    @Test fun detectsNoResults() {
        val res = ArticleParser.parse("""{"status":"no_results"}""", "https://www.zeit.de/a", "https://archive.ph/newest/...")
        assertEquals(ExtractionResult.NeedsInteraction(InteractionReason.NO_RESULTS), res)
    }

    @Test fun detectsSubmitFormAndSubmitting() {
        assertEquals(
            ExtractionResult.NeedsInteraction(InteractionReason.SUBMIT_FORM),
            ArticleParser.parse("""{"status":"submit_form"}""", "https://www.echo-online.de/a", "https://archive.ph/?url=..."),
        )
        assertEquals(
            ExtractionResult.NeedsInteraction(InteractionReason.SUBMITTING),
            ArticleParser.parse("""{"status":"submitting"}""", "https://www.echo-online.de/a", "https://archive.ph/submit/?url=..."),
        )
    }

    @Test fun parsesRichArticleBlocks() {
        val json = """
            {
              "status": "article",
              "article": {
                "title": "Test Headline",
                "byline": "Von Max Mustermann",
                "publishedDate": "23.11.2026",
                "readingTimeMinutes": 4,
                "blocks": [
                  { "type": "image", "src": "https://archive.ph/img.jpg", "caption": "Foto: dpa" },
                  { "type": "heading", "level": 2, "text": "Zwischenüberschrift" },
                  {
                    "type": "paragraph",
                    "spans": [
                      { "text": "Ein ", "bold": false, "italic": false, "link": null },
                      { "text": "wichtiger", "bold": true, "italic": false, "link": null },
                      { "text": " Link", "bold": false, "italic": false, "link": "https://example.com" }
                    ]
                  },
                  { "type": "quote", "text": "Ein Zitat im Text." },
                  { "type": "list", "ordered": false, "items": ["Punkt eins", "Punkt zwei"] }
                ]
              }
            }
        """.trimIndent()
        val raw = JSONObject.quote(json)
        val res = ArticleParser.parse(raw, "https://www.spiegel.de/artikel", "https://archive.ph/2026/spiegel")
        assertTrue(res is ExtractionResult.ArticleFound)
        val article = (res as ExtractionResult.ArticleFound).article
        assertEquals("Test Headline", article.title)
        assertEquals("spiegel.de", article.siteName)
        assertEquals(4, article.readingTimeMinutes)
        assertEquals(5, article.blocks.size)
        assertEquals(ArticleBlock.Image("https://archive.ph/img.jpg", "Foto: dpa"), article.blocks[0])
        assertEquals(ArticleBlock.Heading(2, "Zwischenüberschrift"), article.blocks[1])
        val p = article.blocks[2] as ArticleBlock.Paragraph
        assertEquals(3, p.spans.size)
        assertTrue(p.spans[1].bold)
        assertEquals("https://example.com", p.spans[2].link)
        assertEquals(ArticleBlock.Quote("Ein Zitat im Text."), article.blocks[3])
        assertEquals(ArticleBlock.BulletList(false, listOf("Punkt eins", "Punkt zwei")), article.blocks[4])
    }

    @Test fun nullOrMalformedReturnsNotAnArticle() {
        assertEquals(ExtractionResult.NotAnArticle, ArticleParser.parse("null", "https://a.de", "https://archive.ph"))
        assertEquals(ExtractionResult.NotAnArticle, ArticleParser.parse("not json", "https://a.de", "https://archive.ph"))
    }

    @Test fun normalizesLanguageTags() {
        assertEquals("de-DE", ArticleParser.normalizeLanguage("de_DE"))
        assertEquals("en", ArticleParser.normalizeLanguage(" en "))
        assertEquals(null, ArticleParser.normalizeLanguage("not a language"))
        assertEquals(null, ArticleParser.normalizeLanguage(""))
    }

    @Test fun guessesLanguageFromFunctionWords() {
        fun para(t: String) = ArticleBlock.Paragraph(listOf(InlineSpan(t, false, false, null)))
        val german = listOf(para("Die Regierung hat sich auf einen Kompromiss geeinigt, der auch für die Länder gilt und nicht mehr verändert wird."))
        val english = listOf(para("The government said that it was not willing to change the plan, and this is the final word for the states."))
        assertEquals("de", ArticleParser.guessLanguage(german))
        assertEquals("en", ArticleParser.guessLanguage(english))
        assertEquals(null, ArticleParser.guessLanguage(listOf(para("Kurz."))))
    }

    @Test fun readsLanguageFromExtractorResult() {
        val json = """{"status":"article","article":{"title":"Ein Titel","language":"de-AT","blocks":[{"type":"paragraph","spans":[{"text":"Hallo"}]}]}}"""
        val res = ArticleParser.parse(json, "https://www.derstandard.at/a", "https://archive.ph/1") as ExtractionResult.ArticleFound
        assertEquals("de-AT", res.article.language)
    }
}
