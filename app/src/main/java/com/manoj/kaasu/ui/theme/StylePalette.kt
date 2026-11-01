package com.manoj.kaasu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import com.manoj.kaasu.ui.home.styles.HomeStyle

/**
 * The whole app is built from three colours per style (design-system "primitive" layer):
 * base (the backdrop), ink (text and lines) and accent (the one signal colour).
 * Everything else is derived from these three, so every screen stays in the same family.
 */
@Immutable
data class Palette3(val base: Color, val ink: Color, val accent: Color) {
    val isDark: Boolean get() = base.luminance() < 0.4f

    // Semantic layer, derived only from the three primitives.
    val text: Color get() = ink
    val muted: Color get() = ink.copy(alpha = if (isDark) 0.62f else 0.66f).compositeOver(base)
    val faint: Color get() = ink.copy(alpha = 0.38f).compositeOver(base)
    val line: Color get() = ink.copy(alpha = if (isDark) 0.09f else 0.11f)
    val step1: Color get() = lerp(base, ink, if (isDark) 0.05f else 0.04f)
    val step2: Color get() = lerp(base, ink, if (isDark) 0.09f else 0.07f)
    val step3: Color get() = lerp(base, ink, if (isDark) 0.14f else 0.11f)
    val onAccent: Color get() = if (accent.luminance() > 0.45f) Color(0xFF0B0B0C) else Color.White

    /** Meaning-only colours (money in, over budget), toned toward the palette so they never shout. */
    val positive: Color get() = lerp(Color(0xFF34C77B), ink, 0.25f)
    val negative: Color get() = lerp(Color(0xFFFF4D3D), ink, 0.15f)
    val caution: Color get() = lerp(Color(0xFFF5A524), ink, 0.15f)
}

fun paletteFor(style: HomeStyle): Palette3 = when (style) {
    HomeStyle.CLASSIC -> Palette3(Color(0xFF0F172A), Color(0xFFF1F5F9), Color(0xFF60A5FA))
    HomeStyle.EMBER -> Palette3(Color(0xFF000000), Color(0xFFF5F1EC), Color(0xFFFF7A1A))
    HomeStyle.SKEUO -> Palette3(Color(0xFF1A1512), Color(0xFFF3EDE5), Color(0xFFD4A84F))
    HomeStyle.NEUMORPH -> Palette3(Color(0xFFE4E9F0), Color(0xFF39424F), Color(0xFF4F7CF7))
    HomeStyle.GLASS -> Palette3(Color(0xFF0D1230), Color(0xFFFFFFFF), Color(0xFF7CF4FF))
    HomeStyle.CLAY -> Palette3(Color(0xFFF8D9C4), Color(0xFF4A2B20), Color(0xFFE0622F))
    HomeStyle.AURORA -> Palette3(Color(0xFF040816), Color(0xFFF2FBFF), Color(0xFF00F5D4))
    HomeStyle.MESH -> Palette3(Color(0xFFFFF4EE), Color(0xFF221C33), Color(0xFFE8455F))
    HomeStyle.DARK -> Palette3(Color(0xFF0A0A0B), Color(0xFFF4F4F6), Color(0xFF8FE3B0))
    HomeStyle.METAL -> Palette3(Color(0xFF1B1D20), Color(0xFFEDEFF2), Color(0xFFFF8A2B))
    HomeStyle.PAPER -> Palette3(Color(0xFFEDE5D6), Color(0xFF2E2922), Color(0xFFB4441B))
    HomeStyle.WOOD -> Palette3(Color(0xFF2F1C11), Color(0xFFFFF2E2), Color(0xFFF2C14E))
    HomeStyle.BIO -> Palette3(Color(0xFF0E2C20), Color(0xFFEAFFF1), Color(0xFFB5F05A))
    HomeStyle.LIQUID -> Palette3(Color(0xFF061D4A), Color(0xFFFFFFFF), Color(0xFF8FDBFF))
    HomeStyle.AI -> Palette3(Color(0xFF08061A), Color(0xFFF1F0FF), Color(0xFF67E8F9))
}
