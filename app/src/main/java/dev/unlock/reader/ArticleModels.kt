package dev.unlock.reader

/** Extrahierter Artikel für die native Leseansicht. */
data class Article(
    val title: String,
    val kicker: String? = null,
    val byline: String? = null,
    val publishedDate: String? = null,
    val siteName: String? = null,
    /** BCP-47-Sprachcode (z. B. `de`, `en-US`) für die Silbentrennung; `null` = Systemsprache. */
    val language: String? = null,
    val readingTimeMinutes: Int = 1,
    val sourceUrl: String,
    val archiveUrl: String,
    val blocks: List<ArticleBlock>,
) {
    /** Ungefähre Wortzahl des extrahierten Texts (für die lokale Statistik). */
    fun wordCount(): Int = blocks.sumOf { block ->
        when (block) {
            is ArticleBlock.Paragraph -> block.spans.sumOf { words(it.text) }
            is ArticleBlock.Heading -> words(block.text)
            is ArticleBlock.Quote -> words(block.text)
            is ArticleBlock.BulletList -> block.items.sumOf(::words)
            is ArticleBlock.Image -> 0
        }
    }

    private fun words(text: String): Int = text.split(WHITESPACE).count { it.isNotBlank() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

sealed interface ArticleBlock {
    data class Paragraph(val spans: List<InlineSpan>) : ArticleBlock
    data class Heading(val level: Int, val text: String) : ArticleBlock
    data class Image(val src: String, val caption: String? = null) : ArticleBlock
    data class Quote(val text: String) : ArticleBlock
    data class BulletList(val ordered: Boolean, val items: List<String>) : ArticleBlock
}

data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val link: String? = null,
)

enum class InteractionReason {
    CAPTCHA,
    NO_RESULTS,
    SUBMIT_FORM,
    SUBMITTING,
    LOAD_ERROR,
}

sealed interface ExtractionResult {
    data class ArticleFound(val article: Article) : ExtractionResult
    data class NeedsInteraction(val reason: InteractionReason) : ExtractionResult
    data object NotAnArticle : ExtractionResult
}
