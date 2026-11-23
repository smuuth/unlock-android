package dev.unlock.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

enum class ReaderTheme(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    SEPIA("sepia"),
    DARK("dark"),
    ;

    companion object {
        val DEFAULT = SYSTEM
        fun fromId(id: String?): ReaderTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

enum class ReaderFont(val id: String, val family: FontFamily) {
    SERIF("serif", FontFamily.Serif),
    SANS("sans", FontFamily.SansSerif),
    ;

    companion object {
        val DEFAULT = SERIF
        fun fromId(id: String?): ReaderFont = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

data class ReaderPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val divider: Color,
    val quoteBackground: Color,
    val isDark: Boolean,
)

@Composable
fun readerPalette(theme: ReaderTheme, systemDark: Boolean): ReaderPalette = when (theme) {
    ReaderTheme.SYSTEM -> ReaderPalette(
        background = MaterialTheme.colorScheme.surface,
        surface = MaterialTheme.colorScheme.surfaceContainer,
        text = MaterialTheme.colorScheme.onSurface,
        secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
        accent = MaterialTheme.colorScheme.primary,
        divider = MaterialTheme.colorScheme.outlineVariant,
        quoteBackground = MaterialTheme.colorScheme.surfaceContainerLow,
        isDark = systemDark,
    )
    ReaderTheme.LIGHT -> ReaderPalette(
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFF3F3F6),
        text = Color(0xFF1A1A1E),
        secondaryText = Color(0xFF5A5A64),
        accent = Color(0xFF4B3FB8),
        divider = Color(0xFFE3E3E8),
        quoteBackground = Color(0xFFF6F6F9),
        isDark = false,
    )
    ReaderTheme.SEPIA -> ReaderPalette(
        background = Color(0xFFF6EFE2),
        surface = Color(0xFFECE2CE),
        text = Color(0xFF33281D),
        secondaryText = Color(0xFF6F5D4B),
        accent = Color(0xFF8C531E),
        divider = Color(0xFFDDD0B8),
        quoteBackground = Color(0xFFEEE5D4),
        isDark = false,
    )
    ReaderTheme.DARK -> ReaderPalette(
        background = Color(0xFF121215),
        surface = Color(0xFF1D1D22),
        text = Color(0xFFE5E3E8),
        secondaryText = Color(0xFFA29FA8),
        accent = Color(0xFFC5BBFF),
        divider = Color(0xFF2B2B32),
        quoteBackground = Color(0xFF1A1A20),
        isDark = true,
    )
}
