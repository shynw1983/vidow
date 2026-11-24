package com.vidfit.converter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Core brand colors
val BrandBlueLightMode = Color(0xFF1D4ED8)
val BrandBlueDarkMode = Color(0xFF60A5FA)
val BrandPrimaryButton = Color(0xFF2563EB)

/**
 * Semantic color tokens guaranteed to maintain strong contrast in both Light and Dark themes.
 */
@Immutable
data class VidFitExtendedColors(
    val pageBackground: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val subtleSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val actionButtonBg: Color,
    val actionButtonText: Color,
    val accentText: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val warningText: Color,
    val successBg: Color,
    val successText: Color,
    val successAccent: Color,
    val progressTrack: Color
)

val LightExtendedColors = VidFitExtendedColors(
    pageBackground = Color(0xFFF3F5F9),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE2E8F0),
    subtleSurface = Color(0xFFEFF3F8),
    primaryText = Color(0xFF0F172A),
    secondaryText = Color(0xFF475569),
    actionButtonBg = Color(0xFF1D4ED8),
    actionButtonText = Color(0xFFFFFFFF),
    accentText = Color(0xFF1D4ED8),
    warningBg = Color(0xFFFEF3C7),
    warningBorder = Color(0xFFF59E0B),
    warningText = Color(0xFF78350F),
    successBg = Color(0xFFDCFCE7),
    successText = Color(0xFF14532D),
    successAccent = Color(0xFF15803D),
    progressTrack = Color(0xFFE2E8F0)
)

val DarkExtendedColors = VidFitExtendedColors(
    pageBackground = Color(0xFF0B1120),
    cardBackground = Color(0xFF162032),
    cardBorder = Color(0xFF293548),
    subtleSurface = Color(0xFF1E293B),
    primaryText = Color(0xFFF8FAFC),
    secondaryText = Color(0xFFCBD5E1),
    actionButtonBg = Color(0xFF2563EB),
    actionButtonText = Color(0xFFFFFFFF),
    accentText = Color(0xFF60A5FA),
    warningBg = Color(0xFF3B2306),
    warningBorder = Color(0xFFD97706),
    warningText = Color(0xFFFDE68A),
    successBg = Color(0xFF0E2F1D),
    successText = Color(0xFFBBF7D0),
    successAccent = Color(0xFF4ADE80),
    progressTrack = Color(0xFF293548)
)

val LocalVidFitColors = staticCompositionLocalOf { LightExtendedColors }
