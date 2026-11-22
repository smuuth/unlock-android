package dev.unlock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Fallback für Android < 12 (kein Dynamic Color): ruhiges Violett, abgestimmt aufs App-Icon. */
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF5A4AC0), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF), onPrimaryContainer = Color(0xFF17005F),
    secondary = Color(0xFF5F5C71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5DFF9), onSecondaryContainer = Color(0xFF1C192B),
    tertiary = Color(0xFF7B5266), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E8), onTertiaryContainer = Color(0xFF2F1123),
    background = Color(0xFFFCF8FF), onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFCF8FF), onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE5E0EC), onSurfaceVariant = Color(0xFF48454E),
    outline = Color(0xFF79757F), outlineVariant = Color(0xFFC9C4D0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0ECF4), surfaceContainerHigh = Color(0xFFEBE6EE),
    surfaceContainerHighest = Color(0xFFE5E1E9),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFC8BFFF), onPrimary = Color(0xFF2C1A8E),
    primaryContainer = Color(0xFF4332A7), onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFFC9C3DC), onSecondary = Color(0xFF312E41),
    secondaryContainer = Color(0xFF484459), onSecondaryContainer = Color(0xFFE5DFF9),
    tertiary = Color(0xFFECB8CF), onTertiary = Color(0xFF482537),
    tertiaryContainer = Color(0xFF613B4E), onTertiaryContainer = Color(0xFFFFD8E8),
    background = Color(0xFF141318), onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF141318), onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF48454E), onSurfaceVariant = Color(0xFFC9C4D0),
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF48454E),
    surfaceContainerLowest = Color(0xFF0F0D13), surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF201F25), surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnlockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDark
        else -> FallbackLight
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography(),
        content = content,
    )
}
