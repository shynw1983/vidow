package com.vidfit.converter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandBlueLightMode,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    background = LightExtendedColors.pageBackground,
    onBackground = LightExtendedColors.primaryText,
    surface = LightExtendedColors.cardBackground,
    onSurface = LightExtendedColors.primaryText,
    surfaceVariant = LightExtendedColors.subtleSurface,
    onSurfaceVariant = LightExtendedColors.secondaryText,
    outline = LightExtendedColors.cardBorder,
    outlineVariant = LightExtendedColors.cardBorder,
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDarkMode,
    onPrimary = Color(0xFF08245B),
    primaryContainer = Color(0xFF172554),
    onPrimaryContainer = Color(0xFFBFDBFE),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    background = DarkExtendedColors.pageBackground,
    onBackground = DarkExtendedColors.primaryText,
    surface = DarkExtendedColors.cardBackground,
    onSurface = DarkExtendedColors.primaryText,
    surfaceVariant = DarkExtendedColors.subtleSurface,
    onSurfaceVariant = DarkExtendedColors.secondaryText,
    outline = DarkExtendedColors.cardBorder,
    outlineVariant = DarkExtendedColors.cardBorder,
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA)
)

object VidFitPalette {
    val colors: VidFitExtendedColors
        @Composable get() = LocalVidFitColors.current
}

@Composable
fun VidFitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalVidFitColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
