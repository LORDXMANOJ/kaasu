package com.manoj.kaasu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal object Palette {
    val Navy950 = Color(0xFF0A1020)
    val Navy900 = Color(0xFF0F172A)
    val Navy850 = Color(0xFF141D31)
    val Navy800 = Color(0xFF192134)
    val Navy750 = Color(0xFF1F2A40)
    val Navy700 = Color(0xFF26324B)
    val Navy600 = Color(0xFF334155)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate50 = Color(0xFFF8FAFC)

    val Blue900 = Color(0xFF1E3A8A)
    val Blue700 = Color(0xFF1E40AF)
    val Blue600 = Color(0xFF2563EB)
    val Blue500 = Color(0xFF3B82F6)
    val Blue400 = Color(0xFF60A5FA)
    val Blue100 = Color(0xFFDBEAFE)

    val Green700 = Color(0xFF047857)
    val Green600 = Color(0xFF059669)
    val Green400 = Color(0xFF34D399)
    val Amber400 = Color(0xFFFBBF24)
    val Amber700 = Color(0xFFB45309)
    val Red400 = Color(0xFFF87171)
    val Red600 = Color(0xFFDC2626)
    val Red700 = Color(0xFFB91C1C)
}

/** Category accent colours. Chosen to stay readable on both navy and white surfaces. */
val CategoryColors = listOf(
    Color(0xFFF59E0B), // amber
    Color(0xFFEF4444), // red
    Color(0xFFF97316), // orange
    Color(0xFFA16207), // brown
    Color(0xFF10B981), // emerald
    Color(0xFF8B5CF6), // violet
    Color(0xFF0EA5E9), // sky
    Color(0xFF84CC16), // lime
    Color(0xFFEC4899), // pink
    Color(0xFFEAB308), // yellow
    Color(0xFF14B8A6), // teal
    Color(0xFFD946EF), // fuchsia
    Color(0xFF6366F1), // indigo
    Color(0xFF64748B), // slate
    Color(0xFF22C55E), // green
    Color(0xFF3B82F6), // blue
)

fun categoryColor(index: Int): Color = CategoryColors[((index % CategoryColors.size) + CategoryColors.size) % CategoryColors.size]

@Immutable
data class KaasuColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val ringTrack: Color,
    val cardBorder: Color,
    val isDark: Boolean,
)

internal val DarkKaasuColors = KaasuColors(
    success = Palette.Green400,
    warning = Palette.Amber400,
    danger = Palette.Red400,
    heroStart = Color(0xFF1E3A8A),
    heroEnd = Color(0xFF0F2A5C),
    onHero = Color.White,
    onHeroMuted = Color(0xFFBFD3F5),
    ringTrack = Color(0x33FFFFFF),
    cardBorder = Color(0x14FFFFFF),
    isDark = true,
)

internal val LightKaasuColors = KaasuColors(
    success = Palette.Green700,
    warning = Palette.Amber700,
    danger = Palette.Red700,
    heroStart = Palette.Blue700,
    heroEnd = Color(0xFF1E3A8A),
    onHero = Color.White,
    onHeroMuted = Color(0xFFD6E2FA),
    ringTrack = Color(0x40FFFFFF),
    cardBorder = Color(0x140F172A),
    isDark = false,
)

val LocalKaasuColors = staticCompositionLocalOf { DarkKaasuColors }
