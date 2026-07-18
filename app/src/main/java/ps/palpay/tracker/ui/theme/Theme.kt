package ps.palpay.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PalPayBrandColor,
    secondary = JawwalGreen,
    background = Color(0xFF0F0F12),
    surface = Color(0xFF1C1C21),
    error = SoftError
)

private val LightColorScheme = lightColorScheme(
    primary = PalPayBrandColor,
    secondary = JawwalGreen,
    background = AppBackground,
    surface = AppSurface,
    error = SoftError
)

@Composable
fun PalPayTrackerAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
