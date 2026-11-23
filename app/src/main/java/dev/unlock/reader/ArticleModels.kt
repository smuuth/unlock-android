package dev.unlock.reader

/** Extrahierter Artikel für die native Leseansicht. */
data class Article(
    val title: String,
    val kicker: String? = null,
    val byline: String? = null,
    val publishedDate: String? = null,
    val siteName: String? = null,
    val readingTimeMinutes: Int = 1,
    val sourceUrl: String,
    val archiveUrl: String,
    val blocks: List<ArticleBlock>,
)

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
    SUBMITTING,
}

sealed interface ExtractionResult {
    data class ArticleFound(val article: Article) : ExtractionResult
    data class NeedsInteraction(val reason: InteractionReason) : ExtractionResult
    data object NotAnArticle : ExtractionResult
}
