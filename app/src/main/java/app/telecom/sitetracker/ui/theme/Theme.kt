package app.telecom.sitetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = SurfaceLight,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = OnBrandPrimaryContainer,
    secondary = BrandPrimary,
    onSecondary = SurfaceLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = CardBorderLight,
    outlineVariant = CardBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = TextPrimaryLight,
    primaryContainer = BrandPrimary,
    onPrimaryContainer = BrandPrimaryContainer,
    secondary = BrandPrimaryLight,
    onSecondary = TextPrimaryLight,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = CardBorderDark,
    outlineVariant = CardBorderDark
)

@Composable
fun TelecomSiteTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
