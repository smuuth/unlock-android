package dev.unlock.reader

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Parst das JSON-Ergebnis von [ArticleExtractorScript] in typisierte Kotlin-Modelle. */
object ArticleParser {

    /**
     * `WebView.evaluateJavascript` liefert Strings als JSON-String-Literal zurück
     * (also doppelt gequotet wie `"{\"status\":\"article\"}"`). Diese Methode entpackt beides.
     */
    fun parse(rawJsResult: String?, sourceUrl: String, archiveUrl: String): ExtractionResult {
        val unwrapped = unwrapJsString(rawJsResult) ?: return ExtractionResult.NotAnArticle
        val root = runCatching { JSONObject(unwrapped) }.getOrNull() ?: return ExtractionResult.NotAnArticle

        return when (root.optString("status")) {
            "captcha" -> ExtractionResult.NeedsInteraction(InteractionReason.CAPTCHA)
            "no_results" -> ExtractionResult.NeedsInteraction(InteractionReason.NO_RESULTS)
            "submit_form" -> ExtractionResult.NeedsInteraction(InteractionReason.SUBMIT_FORM)
            "submitting" -> ExtractionResult.NeedsInteraction(InteractionReason.SUBMITTING)
            "article" -> {
                val obj = root.optJSONObject("article") ?: return ExtractionResult.NotAnArticle
                val blocks = parseBlocks(obj.optJSONArray("blocks"))
                val title = obj.optString("title").trim()
                if (title.isEmpty() || blocks.isEmpty()) return ExtractionResult.NotAnArticle
                ExtractionResult.ArticleFound(
                    Article(
                        title = title,
                        kicker = obj.optStringOrNull("kicker"),
                        byline = obj.optStringOrNull("byline"),
                        publishedDate = obj.optStringOrNull("publishedDate"),
                        siteName = extractHost(sourceUrl),
                        readingTimeMinutes = obj.optInt("readingTimeMinutes", 1).coerceAtLeast(1),
                        sourceUrl = sourceUrl,
                        archiveUrl = archiveUrl,
                        blocks = blocks,
                    ),
                )
            }
            else -> ExtractionResult.NotAnArticle
        }
    }

    internal fun unwrapJsString(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "undefined") return null
        if (trimmed.startsWith('{')) return trimmed
        if (trimmed.startsWith('"')) {
            return runCatching { JSONArray("[$trimmed]").getString(0) }.getOrNull()
        }
        return null
    }

    internal fun extractHost(url: String): String? =
        runCatching { URI(url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() } }.getOrNull()

    private fun parseBlocks(array: JSONArray?): List<ArticleBlock> {
        if (array == null) return emptyList()
        val result = ArrayList<ArticleBlock>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            when (obj.optString("type")) {
                "paragraph" -> {
                    val spans = parseSpans(obj.optJSONArray("spans"))
                    if (spans.isNotEmpty()) result += ArticleBlock.Paragraph(spans)
                }
                "heading" -> {
                    val text = obj.optString("text").trim()
                    if (text.isNotEmpty()) {
                        result += ArticleBlock.Heading(
                            level = obj.optInt("level", 2).coerceIn(2, 4),
                            text = text,
                        )
                    }
                }
                "image" -> {
                    val src = obj.optString("src").trim()
                    if (src.isNotEmpty()) {
                        result += ArticleBlock.Image(
                            src = src,
                            caption = obj.optStringOrNull("caption"),
                        )
                    }
                }
                "quote" -> {
                    val text = obj.optString("text").trim()
                    if (text.isNotEmpty()) result += ArticleBlock.Quote(text)
                }
                "list" -> {
                    val itemsArr = obj.optJSONArray("items") ?: continue
                    val items = buildList {
                        for (j in 0 until itemsArr.length()) {
                            val t = itemsArr.optString(j).trim()
                            if (t.isNotEmpty()) add(t)
                        }
                    }
                    if (items.isNotEmpty()) {
                        result += ArticleBlock.BulletList(
                            ordered = obj.optBoolean("ordered", false),
                            items = items,
                        )
                    }
                }
            }
        }
        return result
    }

    private fun parseSpans(array: JSONArray?): List<InlineSpan> {
        if (array == null) return emptyList()
        val result = ArrayList<InlineSpan>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val text = obj.optString("text")
            if (text.isNotEmpty()) {
                result += InlineSpan(
                    text = text,
                    bold = obj.optBoolean("bold", false),
                    italic = obj.optBoolean("italic", false),
                    link = obj.optStringOrNull("link"),
                )
            }
        }
        return result
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() }
    }
}
