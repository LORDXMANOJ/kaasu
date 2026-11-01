package com.manoj.kaasu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.Blue400,
    onPrimary = Palette.Navy950,
    primaryContainer = Palette.Blue700,
    onPrimaryContainer = Color.White,
    secondary = Palette.Green400,
    onSecondary = Palette.Navy950,
    secondaryContainer = Color(0xFF0B3B2E),
    onSecondaryContainer = Color(0xFFB7F5DC),
    tertiary = Palette.Amber400,
    onTertiary = Palette.Navy950,
    background = Palette.Navy900,
    onBackground = Palette.Slate100,
    surface = Palette.Navy900,
    onSurface = Palette.Slate100,
    surfaceVariant = Palette.Navy800,
    onSurfaceVariant = Palette.Slate400,
    surfaceContainerLowest = Palette.Navy950,
    surfaceContainerLow = Palette.Navy850,
    surfaceContainer = Palette.Navy800,
    surfaceContainerHigh = Palette.Navy750,
    surfaceContainerHighest = Palette.Navy700,
    surfaceBright = Palette.Navy700,
    surfaceDim = Palette.Navy950,
    inverseSurface = Palette.Slate100,
    inverseOnSurface = Palette.Navy900,
    outline = Palette.Navy600,
    outlineVariant = Color(0xFF1E293B),
    error = Palette.Red400,
    onError = Palette.Navy950,
    errorContainer = Color(0xFF4C1D1D),
    onErrorContainer = Color(0xFFFECACA),
    scrim = Color.Black,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Palette.Blue700,
    onPrimary = Color.White,
    primaryContainer = Palette.Blue100,
    onPrimaryContainer = Palette.Blue900,
    secondary = Palette.Green700,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Palette.Amber700,
    onTertiary = Color.White,
    background = Palette.Slate100,
    onBackground = Palette.Navy900,
    surface = Palette.Slate100,
    onSurface = Palette.Navy900,
    surfaceVariant = Palette.Slate200,
    onSurfaceVariant = Palette.Slate600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.Slate50,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Palette.Slate50,
    surfaceContainerHighest = Palette.Slate200,
    surfaceBright = Color.White,
    surfaceDim = Palette.Slate200,
    inverseSurface = Palette.Navy900,
    inverseOnSurface = Palette.Slate100,
    outline = Palette.Slate300,
    outlineVariant = Palette.Slate200,
    error = Palette.Red700,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    scrim = Color.Black,
)

val KaasuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Builds the Material colours for every screen from a style's three colours. Pages are transparent over the live wallpaper. */
fun schemeFrom(p: Palette3): ColorScheme {
    val common = if (p.isDark) darkColorScheme() else lightColorScheme()
    return common.copy(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.step2,
        onPrimaryContainer = p.ink,
        secondary = p.accent,
        onSecondary = p.onAccent,
        secondaryContainer = p.step2,
        onSecondaryContainer = p.ink,
        tertiary = p.accent,
        onTertiary = p.onAccent,
        background = p.base,
        onBackground = p.ink,
        surface = p.step1,
        onSurface = p.ink,
        surfaceVariant = p.step2,
        onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.base,
        surfaceContainerLow = p.step1,
        surfaceContainer = p.step1,
        surfaceContainerHigh = p.step2,
        surfaceContainerHighest = p.step3,
        surfaceBright = p.step3,
        surfaceDim = p.base,
        inverseSurface = p.ink,
        inverseOnSurface = p.base,
        outline = p.ink.copy(alpha = 0.22f),
        outlineVariant = p.line,
        error = p.negative,
        onError = p.onAccent,
        errorContainer = p.step2,
        onErrorContainer = p.negative,
        scrim = Color.Black,
    )
}

fun kaasuColorsFrom(p: Palette3) = KaasuColors(
    success = p.positive,
    warning = p.caution,
    danger = p.negative,
    heroStart = p.step2,
    heroEnd = p.step1,
    onHero = p.ink,
    onHeroMuted = p.muted,
    ringTrack = p.line,
    cardBorder = p.line,
    isDark = p.isDark,
)

/** The app theme for the chosen style: three colours, the Geist type scale and calm corners. */
@Composable
fun KaasuTheme(palette: Palette3, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKaasuColors provides kaasuColorsFrom(palette), LocalPalette provides palette) {
        MaterialTheme(colorScheme = schemeFrom(palette), typography = KaasuTypography, shapes = KaasuShapes) {
            // Transparent surfaces would otherwise fall back to black text; ink is the default everywhere.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides palette.ink, content = content)
        }
    }
}

val LocalPalette = androidx.compose.runtime.staticCompositionLocalOf { paletteFor(com.manoj.kaasu.ui.home.styles.HomeStyle.EMBER) }

@Composable
fun KaasuTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKaasuColors provides if (darkTheme) DarkKaasuColors else LightKaasuColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = KaasuTypography,
            shapes = KaasuShapes,
            content = content,
        )
    }
}

object KaasuTheme {
    val colors: KaasuColors
        @Composable @ReadOnlyComposable get() = LocalKaasuColors.current
    val palette: Palette3
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}
