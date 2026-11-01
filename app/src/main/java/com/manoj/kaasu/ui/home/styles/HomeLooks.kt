package com.manoj.kaasu.ui.home.styles

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.lerp
import com.manoj.kaasu.ui.theme.Palette3
import com.manoj.kaasu.ui.theme.paletteFor

/** The Home screen styles the user can switch between. CLASSIC is the original screen. */
enum class HomeStyle(val label: String, val tagline: String) {
    CLASSIC("Classic", "The original Kaasu"),
    EMBER("Ember", "Pitch black, live embers"),
    SKEUO("Skeuomorphism", "Real-world materials"),
    NEUMORPH("Neumorphism", "Soft minimalism"),
    GLASS("Glassmorphism", "Frosted transparency"),
    CLAY("Claymorphism", "Playful and soft"),
    AURORA("Aurora", "Dynamic glow"),
    MESH("Mesh", "Colourful harmony"),
    DARK("Dark", "Bold elegance"),
    METAL("Metal", "Industrial precision"),
    PAPER("Paper", "Natural simplicity"),
    WOOD("Wood", "Warm naturalism"),
    BIO("Bio-morphism", "Inspired by nature"),
    LIQUID("Liquid Glass", "Fluid clarity"),
    AI("AI Morphism", "Intelligent glow"),
    ;

    companion object {
        fun from(name: String?): HomeStyle = entries.firstOrNull { it.name == name } ?: EMBER
    }
}

enum class Texture { NONE, PAPER, WOOD, BRUSHED, GRAIN, BUBBLES, SPARKLES }

/** Slow, live backdrops. They stop moving when the phone's animations are turned off. */
enum class Wallpaper { NONE, DRIFT, SHEEN, EMBERS, BUBBLES, SPARKLES }

@Immutable
data class Shadow(val color: Color, val dx: Dp, val dy: Dp, val blur: Dp)

/** Per-corner radii, so Bio-morphism can use organic, uneven shapes. */
@Immutable
data class Corners(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp) {
    constructor(all: Dp) : this(all, all, all, all)
}

/** A blurred colour light drawn behind everything (aurora, mesh, glass backdrops). Position and size are fractions of the screen. */
@Immutable
data class Glow(val color: Color, val x: Float, val y: Float, val radius: Float)

@Immutable
data class SurfaceLook(
    val fill: Brush,
    val border: Brush? = null,
    val borderWidth: Dp = 1.dp,
    val shadows: List<Shadow> = emptyList(),
    val inner: List<Shadow> = emptyList(),
    val gloss: Brush? = null,
    val texture: Texture = Texture.NONE,
    val glows: List<Glow> = emptyList(),
)

/** Every token one Home style needs. Nothing on the styled Home screen uses a colour from outside its look. */
@Immutable
data class HomeLook(
    val style: HomeStyle,
    val isDark: Boolean,
    val background: Brush,
    val backgroundGlows: List<Glow> = emptyList(),
    val backgroundTexture: Texture = Texture.NONE,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val positive: Color,
    val warning: Color,
    val hero: SurfaceLook,
    val heroText: Color,
    val heroMuted: Color,
    val ringFill: Color,
    val ringTrack: Color,
    val card: SurfaceLook,
    val action: SurfaceLook,
    val actionText: Color,
    val primaryAction: SurfaceLook,
    val primaryActionText: Color,
    /** Buttons that sit on cards or the backdrop (header, meals); `action` is only for inside the hero. */
    val chip: SurfaceLook = action,
    val chipText: Color = actionText,
    /** Floating tab bar: the pill, its glass lens and icon colours. */
    val bar: SurfaceLook = card,
    val lens: SurfaceLook = chip,
    val lensText: Color = chipText,
    val barIcon: Color = muted,
    /** Flat styles drop card surfaces and separate sections with hairlines instead. */
    val flat: Boolean = false,
    val line: Color = muted.copy(alpha = 0.18f),
    val wallpaper: Wallpaper = Wallpaper.NONE,
    val corners: Corners = Corners(24.dp),
    val heroCorners: Corners = Corners(28.dp),
    val amountWeight: FontWeight = FontWeight.ExtraBold,
)

private fun v(a: Color, b: Color, c: Color? = null) = Brush.verticalGradient(listOfNotNull(a, b, c))
private fun d(a: Color, b: Color, c: Color? = null, e: Color? = null) =
    Brush.linearGradient(listOfNotNull(a, b, c, e), start = Offset.Zero, end = Offset.Infinite)
private fun solid(c: Color) = Brush.verticalGradient(listOf(c, c))
/** A highlight that fades to nothing by the middle of the surface, so there is never a hard edge. */
private fun gloss(alpha: Float) = Brush.verticalGradient(
    0f to Color.White.copy(alpha = alpha),
    0.25f to Color.White.copy(alpha = alpha * 0.45f),
    0.55f to Color.White.copy(alpha = 0f),
    1f to Color.White.copy(alpha = 0f),
)
private val topGloss = gloss(0.32f)

/** Every style is flat (no cards) and coloured only from its three-colour palette; materials stay on buttons, bar and backdrop. */
fun lookFor(style: HomeStyle): HomeLook = rawLook(style).harmonize(paletteFor(style), wallpaperFor(style))

private fun wallpaperFor(style: HomeStyle): Wallpaper = when (style) {
    HomeStyle.EMBER -> Wallpaper.EMBERS
    HomeStyle.BIO -> Wallpaper.BUBBLES
    HomeStyle.AI -> Wallpaper.SPARKLES
    HomeStyle.GLASS, HomeStyle.AURORA, HomeStyle.MESH, HomeStyle.LIQUID, HomeStyle.CLASSIC, HomeStyle.DARK -> Wallpaper.DRIFT
    HomeStyle.SKEUO, HomeStyle.NEUMORPH, HomeStyle.CLAY, HomeStyle.METAL, HomeStyle.PAPER, HomeStyle.WOOD -> Wallpaper.SHEEN
}

private fun HomeLook.harmonize(p: Palette3, wall: Wallpaper): HomeLook {
    val none = SurfaceLook(fill = solid(Color.Transparent))
    val accentFill = v(lerp(p.accent, Color.White, 0.12f), lerp(p.accent, Color.Black, 0.06f))
    return copy(
        isDark = p.isDark,
        text = p.ink, muted = p.muted, accent = p.accent, positive = p.positive, warning = p.negative,
        hero = none, card = none,
        heroText = p.ink, heroMuted = p.muted,
        ringFill = p.accent, ringTrack = p.line,
        action = action.copy(fill = v(p.step2, p.step1)), actionText = p.ink,
        chip = chip.copy(fill = v(p.step2, p.step1)), chipText = p.ink,
        primaryAction = primaryAction.copy(fill = accentFill), primaryActionText = p.onAccent,
        bar = bar.copy(fill = solid(p.step1.copy(alpha = 0.94f))),
        lens = lens.copy(fill = v(p.step3, p.step2)), lensText = p.accent, barIcon = p.muted,
        flat = true, line = p.line, wallpaper = wall,
    )
}

private fun rawLook(style: HomeStyle): HomeLook = when (style) {
    HomeStyle.CLASSIC -> classic()
    HomeStyle.EMBER -> ember()
    HomeStyle.DARK -> dark()
    HomeStyle.SKEUO -> skeuo()
    HomeStyle.NEUMORPH -> neumorph()
    HomeStyle.GLASS -> glass()
    HomeStyle.CLAY -> clay()
    HomeStyle.AURORA -> aurora()
    HomeStyle.MESH -> mesh()
    HomeStyle.METAL -> metal()
    HomeStyle.PAPER -> paper()
    HomeStyle.WOOD -> wood()
    HomeStyle.BIO -> bio()
    HomeStyle.LIQUID -> liquid()
    HomeStyle.AI -> ai()
}

/**
 * Ember: pitch black, no cards. Sections sit on the black and are split by hairlines; orange is the one
 * accent and red is kept for "over budget". Depth comes from surface steps, not shadows (unslop).
 */
private fun ember(): HomeLook {
    val orange = Color(0xFFFF7A1A)
    val none = SurfaceLook(fill = solid(Color.Transparent))
    val step = SurfaceLook(fill = solid(Color(0xFF141210)), border = solid(Color.White.copy(alpha = 0.08f)))
    return HomeLook(
        style = HomeStyle.EMBER, isDark = true,
        background = solid(Color.Black),
        text = Color(0xFFF5F1EC), muted = Color(0xFF8C857D), accent = orange,
        positive = Color(0xFF9BCB8A), warning = Color(0xFFFF453A),
        hero = none,
        heroText = Color(0xFFF5F1EC), heroMuted = Color(0xFF8C857D),
        ringFill = orange, ringTrack = Color(0xFF1E1B18),
        card = none,
        action = step,
        actionText = Color(0xFFF5F1EC),
        primaryAction = SurfaceLook(fill = v(Color(0xFFFF8A2E), Color(0xFFFF6A12))),
        primaryActionText = Color(0xFF1A0A00),
        bar = SurfaceLook(fill = solid(Color(0xF20E0D0C)), border = solid(Color.White.copy(alpha = 0.09f)), shadows = listOf(Shadow(Color(0xCC000000), 0.dp, 10.dp, 24.dp))),
        lens = SurfaceLook(
            fill = v(Color(0xFF2A221C), Color(0xFF1A1512)),
            border = v(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f), Color(0x66FF7A1A)),
            borderWidth = 1.2.dp,
            gloss = gloss(0.18f),
            shadows = listOf(Shadow(Color(0x99000000), 0.dp, 6.dp, 14.dp)),
        ),
        lensText = orange,
        barIcon = Color(0xFF8C857D),
        flat = true,
        line = Color.White.copy(alpha = 0.08f),
        wallpaper = Wallpaper.EMBERS,
        corners = Corners(16.dp), heroCorners = Corners(16.dp), amountWeight = FontWeight.SemiBold,
    )
}

/** Only used for the picker preview; the Classic Home screen itself is the original layout. */
private fun classic(): HomeLook = dark().copy(
    style = HomeStyle.CLASSIC,
    background = v(Color(0xFF0F172A), Color(0xFF0B1222)),
    backgroundGlows = emptyList(),
    hero = SurfaceLook(fill = d(Color(0xFF1E3A8A), Color(0xFF172554))),
    heroText = Color.White,
    heroMuted = Color(0xFFBFD3FF),
    card = SurfaceLook(fill = solid(Color(0xFF1B2436)), border = solid(Color(0xFF2A3550))),
)

private fun skeuo(): HomeLook {
    val leather = SurfaceLook(
        fill = v(Color(0xFF332C27), Color(0xFF221D1A)),
        border = v(Color(0x33FFFFFF), Color(0x66000000)),
        shadows = listOf(Shadow(Color(0xAA000000), 0.dp, 6.dp, 14.dp)),
        inner = listOf(Shadow(Color(0x22FFFFFF), 0.dp, 1.dp, 1.dp)),
        gloss = gloss(0.07f),
        texture = Texture.GRAIN,
    )
    return HomeLook(
        style = HomeStyle.SKEUO, isDark = true,
        background = v(Color(0xFF2B2420), Color(0xFF141110)),
        backgroundTexture = Texture.GRAIN,
        text = Color(0xFFF3EDE5), muted = Color(0xFFB8AC9F), accent = Color(0xFFD4A84F),
        positive = Color(0xFF8FD18F), warning = Color(0xFFE9B35C),
        hero = SurfaceLook(
            fill = v(Color(0xFFE8EAED), Color(0xFFB9BDC2), Color(0xFF9DA2A8)),
            border = v(Color(0xFFFFFFFF), Color(0xFF5E6268)),
            borderWidth = 1.5.dp,
            shadows = listOf(Shadow(Color(0xCC000000), 0.dp, 10.dp, 22.dp)),
            gloss = topGloss,
            texture = Texture.BRUSHED,
        ),
        heroText = Color(0xFF1C1E21), heroMuted = Color(0xFF4A4E54),
        ringFill = Color(0xFF2E6BD9), ringTrack = Color(0x33000000),
        card = leather,
        action = SurfaceLook(fill = v(Color(0xFF2A2522), Color(0xFF15120F)), border = v(Color(0x55FFFFFF), Color(0x88000000)), shadows = listOf(Shadow(Color(0x88000000), 0.dp, 3.dp, 6.dp)), gloss = topGloss),
        actionText = Color(0xFFF3EDE5),
        primaryAction = SurfaceLook(fill = v(Color(0xFFE2BC63), Color(0xFFA9802E)), border = v(Color(0xFFFFE7A6), Color(0xFF6B4E15)), shadows = listOf(Shadow(Color(0x99000000), 0.dp, 3.dp, 6.dp)), gloss = topGloss),
        primaryActionText = Color(0xFF2A1E05),
        corners = Corners(18.dp), heroCorners = Corners(22.dp),
    )
}

private fun neumorph(): HomeLook {
    val base = Color(0xFFE4E9F0)
    val raised = SurfaceLook(
        fill = solid(base),
        shadows = listOf(Shadow(Color(0xFFFFFFFF), (-7).dp, (-7).dp, 14.dp), Shadow(Color(0x66A3B1C6), 7.dp, 7.dp, 14.dp)),
    )
    return HomeLook(
        style = HomeStyle.NEUMORPH, isDark = false,
        background = solid(base),
        text = Color(0xFF39424F), muted = Color(0xFF7D8796), accent = Color(0xFF4F7CF7),
        positive = Color(0xFF2F9E6E), warning = Color(0xFFD08A1E),
        hero = raised.copy(shadows = listOf(Shadow(Color(0xFFFFFFFF), (-10).dp, (-10).dp, 20.dp), Shadow(Color(0x80A3B1C6), 10.dp, 10.dp, 20.dp))),
        heroText = Color(0xFF2F3743), heroMuted = Color(0xFF7D8796),
        ringFill = Color(0xFF4F7CF7), ringTrack = Color(0xFFD3DAE4),
        card = raised,
        action = SurfaceLook(fill = solid(base), inner = listOf(Shadow(Color(0x66A3B1C6), 4.dp, 4.dp, 8.dp), Shadow(Color(0xFFFFFFFF), (-4).dp, (-4).dp, 8.dp))),
        actionText = Color(0xFF39424F),
        primaryAction = SurfaceLook(fill = v(Color(0xFF6A90FA), Color(0xFF4570EE)), shadows = listOf(Shadow(Color(0x804570EE), 4.dp, 6.dp, 12.dp), Shadow(Color(0xFFFFFFFF), (-4).dp, (-4).dp, 10.dp))),
        primaryActionText = Color.White,
        corners = Corners(26.dp), heroCorners = Corners(32.dp), amountWeight = FontWeight.Bold,
    )
}

private fun glass(): HomeLook {
    val frosted = SurfaceLook(
        fill = v(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.07f)),
        border = d(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.25f)),
        shadows = listOf(Shadow(Color(0x40000000), 0.dp, 10.dp, 24.dp)),
        gloss = gloss(0.14f),
    )
    return HomeLook(
        style = HomeStyle.GLASS, isDark = true,
        background = v(Color(0xFF101634), Color(0xFF0A0E22)),
        backgroundGlows = listOf(Glow(Color(0xFFE040FB), 0.1f, 0.12f, 0.55f), Glow(Color(0xFF00E5FF), 0.95f, 0.3f, 0.5f), Glow(Color(0xFFFFAB40), 0.2f, 0.75f, 0.5f), Glow(Color(0xFF536DFE), 0.9f, 0.9f, 0.45f)),
        text = Color.White, muted = Color.White.copy(alpha = 0.68f), accent = Color(0xFF7CF4FF),
        positive = Color(0xFF7CF7B6), warning = Color(0xFFFFD27C),
        hero = frosted.copy(fill = v(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.1f))),
        heroText = Color.White, heroMuted = Color.White.copy(alpha = 0.72f),
        ringFill = Color(0xFF7CF4FF), ringTrack = Color.White.copy(alpha = 0.18f),
        card = frosted,
        action = frosted.copy(shadows = emptyList()),
        actionText = Color.White,
        primaryAction = SurfaceLook(fill = v(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.8f)), shadows = listOf(Shadow(Color(0x40000000), 0.dp, 6.dp, 14.dp))),
        primaryActionText = Color(0xFF1A1F3D),
        corners = Corners(26.dp), heroCorners = Corners(30.dp),
    )
}

private fun clay(): HomeLook {
    fun clayOf(top: Color, bottom: Color, shadow: Color) = SurfaceLook(
        fill = v(top, bottom),
        shadows = listOf(Shadow(shadow, 8.dp, 12.dp, 20.dp)),
        inner = listOf(Shadow(Color(0x99FFFFFF), (-6).dp, (-6).dp, 10.dp), Shadow(Color(0x33000000), 6.dp, 8.dp, 12.dp)),
    )
    return HomeLook(
        style = HomeStyle.CLAY, isDark = false,
        background = v(Color(0xFFFBE3D3), Color(0xFFF2C9AE)),
        text = Color(0xFF4A2B20), muted = Color(0xFF8C6355), accent = Color(0xFFE8744A),
        positive = Color(0xFF3E9B5B), warning = Color(0xFFC77A16),
        hero = clayOf(Color(0xFFFFA27A), Color(0xFFEE7D52), Color(0x66C2552E)),
        heroText = Color(0xFF3B1A0E), heroMuted = Color(0xFF6E3420),
        ringFill = Color(0xFF3B1A0E), ringTrack = Color(0x33FFFFFF),
        card = clayOf(Color(0xFFFFF1E8), Color(0xFFFBDDCB), Color(0x40C2552E)),
        action = clayOf(Color(0xFFFFE1D0), Color(0xFFF8C7AD), Color(0x40C2552E)).copy(shadows = listOf(Shadow(Color(0x30C2552E), 4.dp, 6.dp, 10.dp))),
        actionText = Color(0xFF4A2B20),
        primaryAction = clayOf(Color(0xFF6FD3A8), Color(0xFF3DB585), Color(0x55217A55)).copy(shadows = listOf(Shadow(Color(0x55217A55), 4.dp, 6.dp, 10.dp))),
        primaryActionText = Color(0xFF0F3B28),
        corners = Corners(30.dp), heroCorners = Corners(36.dp),
    )
}

private fun aurora(): HomeLook {
    val dusk = SurfaceLook(
        fill = v(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f)),
        border = d(Color(0x8800F5D4), Color(0x11FFFFFF), Color(0x66FF5DA2)),
        shadows = listOf(Shadow(Color(0x5500F5D4), 0.dp, 0.dp, 18.dp)),
    )
    return HomeLook(
        style = HomeStyle.AURORA, isDark = true,
        background = v(Color(0xFF050A1C), Color(0xFF02040C)),
        backgroundGlows = listOf(Glow(Color(0xFF00F5D4), 0.15f, 0.05f, 0.6f), Glow(Color(0xFF38F27E), 0.8f, 0.18f, 0.5f), Glow(Color(0xFFFF5DA2), 0.9f, 0.6f, 0.5f), Glow(Color(0xFF2DD4FF), 0.05f, 0.85f, 0.5f)),
        text = Color(0xFFF2FBFF), muted = Color(0xFFA7C4CC), accent = Color(0xFF00F5D4),
        positive = Color(0xFF6CF7B4), warning = Color(0xFFFFC56B),
        hero = SurfaceLook(
            fill = d(Color(0xFF0BD3B5), Color(0xFF1C9C8E), Color(0xFF8C3BBF)),
            border = d(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f)),
            shadows = listOf(Shadow(Color(0x8800F5D4), 0.dp, 8.dp, 28.dp)),
            gloss = gloss(0.22f),
            glows = listOf(Glow(Color(0xFF38F27E), 0.2f, 0.1f, 0.7f), Glow(Color(0xFFFF5DA2), 0.95f, 0.95f, 0.6f)),
        ),
        heroText = Color.White, heroMuted = Color.White.copy(alpha = 0.78f),
        ringFill = Color.White, ringTrack = Color.White.copy(alpha = 0.25f),
        card = dusk,
        action = SurfaceLook(fill = v(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f)), border = solid(Color.White.copy(alpha = 0.3f))),
        actionText = Color.White,
        primaryAction = SurfaceLook(fill = solid(Color.White), shadows = listOf(Shadow(Color(0x6600F5D4), 0.dp, 4.dp, 14.dp))),
        primaryActionText = Color(0xFF05241F),
        corners = Corners(24.dp), heroCorners = Corners(30.dp),
    )
}

private fun mesh(): HomeLook {
    val milk = SurfaceLook(
        fill = v(Color.White.copy(alpha = 0.82f), Color.White.copy(alpha = 0.7f)),
        border = solid(Color.White),
        shadows = listOf(Shadow(Color(0x26B4537A), 0.dp, 10.dp, 24.dp)),
    )
    return HomeLook(
        style = HomeStyle.MESH, isDark = false,
        background = v(Color(0xFFFFF4EE), Color(0xFFF4F1FF)),
        backgroundGlows = listOf(Glow(Color(0xFFFFB199), 0.0f, 0.0f, 0.6f), Glow(Color(0xFF9BD7FF), 1f, 0.25f, 0.6f), Glow(Color(0xFFFFC2E2), 0.1f, 0.8f, 0.6f), Glow(Color(0xFFFFE08A), 0.9f, 0.95f, 0.5f)),
        text = Color(0xFF221C33), muted = Color(0xFF6E6680), accent = Color(0xFFFF5C7A),
        positive = Color(0xFF1F9E6A), warning = Color(0xFFD9811A),
        hero = SurfaceLook(
            fill = solid(Color(0xFFFF8A65)),
            glows = listOf(Glow(Color(0xFFFF4D8D), 0.0f, 0.0f, 0.9f), Glow(Color(0xFFFFB347), 1f, 0.0f, 0.8f), Glow(Color(0xFF4DA3FF), 1f, 1f, 0.9f), Glow(Color(0xFFB26BFF), 0f, 1f, 0.7f)),
            shadows = listOf(Shadow(Color(0x55FF5C7A), 0.dp, 14.dp, 28.dp)),
            border = solid(Color.White.copy(alpha = 0.5f)),
        ),
        heroText = Color.White, heroMuted = Color.White.copy(alpha = 0.85f),
        ringFill = Color.White, ringTrack = Color.White.copy(alpha = 0.3f),
        card = milk,
        action = SurfaceLook(fill = solid(Color.White.copy(alpha = 0.28f)), border = solid(Color.White.copy(alpha = 0.6f))),
        actionText = Color.White,
        primaryAction = SurfaceLook(fill = solid(Color.White), shadows = listOf(Shadow(Color(0x33000000), 0.dp, 4.dp, 10.dp))),
        primaryActionText = Color(0xFF221C33),
        chip = SurfaceLook(fill = v(Color(0xFFFFF0F3), Color(0xFFFFE3EA)), border = solid(Color.White), shadows = listOf(Shadow(Color(0x1FB4537A), 0.dp, 4.dp, 10.dp))),
        chipText = Color(0xFF3A2440),
        corners = Corners(26.dp), heroCorners = Corners(32.dp),
    )
}

private fun dark(): HomeLook {
    val matte = SurfaceLook(
        fill = v(Color(0xFF1A1A1E), Color(0xFF141417)),
        border = v(Color(0xFF2C2C32), Color(0xFF1C1C20)),
        shadows = listOf(Shadow(Color(0x99000000), 0.dp, 8.dp, 18.dp)),
    )
    return HomeLook(
        style = HomeStyle.DARK, isDark = true,
        background = v(Color(0xFF0C0C0E), Color(0xFF060607)),
        backgroundGlows = listOf(Glow(Color(0x337EE2A8), 0.9f, 0.0f, 0.5f)),
        text = Color(0xFFF4F4F6), muted = Color(0xFF8E8E96), accent = Color(0xFF8FE3B0),
        positive = Color(0xFF8FE3B0), warning = Color(0xFFE8C07A),
        hero = SurfaceLook(
            fill = v(Color(0xFF202024), Color(0xFF151518)),
            border = v(Color(0xFF3A3A40), Color(0xFF1C1C20)),
            shadows = listOf(Shadow(Color(0xCC000000), 0.dp, 12.dp, 26.dp)),
            gloss = gloss(0.05f),
        ),
        heroText = Color(0xFFF4F4F6), heroMuted = Color(0xFF8E8E96),
        ringFill = Color(0xFF8FE3B0), ringTrack = Color(0xFF2A2A30),
        card = matte,
        action = SurfaceLook(fill = solid(Color(0xFF26262B)), border = solid(Color(0xFF33333A))),
        actionText = Color(0xFFF4F4F6),
        primaryAction = SurfaceLook(fill = v(Color(0xFFA6EEC2), Color(0xFF7FDDA6))),
        primaryActionText = Color(0xFF0B2416),
        corners = Corners(24.dp), heroCorners = Corners(30.dp),
    )
}

private fun metal(): HomeLook {
    val steel = SurfaceLook(
        fill = v(Color(0xFF45484E), Color(0xFF2B2D31)),
        border = v(Color(0xFF8A8E95), Color(0xFF15161A)),
        borderWidth = 1.5.dp,
        shadows = listOf(Shadow(Color(0xCC000000), 0.dp, 8.dp, 16.dp)),
        gloss = gloss(0.10f),
        texture = Texture.BRUSHED,
    )
    return HomeLook(
        style = HomeStyle.METAL, isDark = true,
        background = v(Color(0xFF26282C), Color(0xFF111214)),
        backgroundTexture = Texture.BRUSHED,
        text = Color(0xFFEDEFF2), muted = Color(0xFFA0A5AD), accent = Color(0xFFFF8A2B),
        positive = Color(0xFF7BD88F), warning = Color(0xFFFFB35C),
        hero = SurfaceLook(
            fill = d(Color(0xFFF1F3F5), Color(0xFFB6BBC1), Color(0xFFE2E5E9), Color(0xFF9EA4AB)),
            border = v(Color(0xFFFFFFFF), Color(0xFF55595F)),
            borderWidth = 2.dp,
            shadows = listOf(Shadow(Color(0xEE000000), 0.dp, 12.dp, 24.dp)),
            gloss = topGloss,
            texture = Texture.BRUSHED,
        ),
        heroText = Color(0xFF1A1C1F), heroMuted = Color(0xFF4B5057),
        ringFill = Color(0xFFFF8A2B), ringTrack = Color(0x33000000),
        card = steel,
        action = SurfaceLook(fill = v(Color(0xFFCDD1D6), Color(0xFF8F949B)), border = v(Color.White, Color(0xFF4B4F55)), shadows = listOf(Shadow(Color(0x88000000), 0.dp, 3.dp, 6.dp)), texture = Texture.BRUSHED, gloss = topGloss),
        actionText = Color(0xFF1A1C1F),
        primaryAction = SurfaceLook(fill = v(Color(0xFFFFA45C), Color(0xFFE66F12)), border = v(Color(0xFFFFD3AA), Color(0xFF8A3D00)), shadows = listOf(Shadow(Color(0x88000000), 0.dp, 3.dp, 6.dp)), gloss = topGloss),
        primaryActionText = Color(0xFF2B1300),
        corners = Corners(16.dp), heroCorners = Corners(20.dp),
    )
}

private fun paper(): HomeLook {
    val sheet = SurfaceLook(
        fill = solid(Color(0xFFFCF8F1)),
        shadows = listOf(Shadow(Color(0x22573F1C), 0.dp, 2.dp, 3.dp), Shadow(Color(0x1A573F1C), 0.dp, 8.dp, 16.dp)),
        texture = Texture.PAPER,
    )
    return HomeLook(
        style = HomeStyle.PAPER, isDark = false,
        background = solid(Color(0xFFEDE5D6)),
        backgroundTexture = Texture.PAPER,
        text = Color(0xFF2E2922), muted = Color(0xFF7A7064), accent = Color(0xFFB4441B),
        positive = Color(0xFF3D7A4A), warning = Color(0xFFA86A12),
        hero = sheet.copy(fill = solid(Color(0xFFFFFDF8)), shadows = listOf(Shadow(Color(0x26573F1C), 0.dp, 3.dp, 4.dp), Shadow(Color(0x22573F1C), 6.dp, 14.dp, 22.dp))),
        heroText = Color(0xFF2E2922), heroMuted = Color(0xFF7A7064),
        ringFill = Color(0xFFB4441B), ringTrack = Color(0xFFE8DFCF),
        card = sheet,
        action = SurfaceLook(fill = solid(Color(0xFFF3ECDF)), border = solid(Color(0xFFDCCFB8)), texture = Texture.PAPER),
        actionText = Color(0xFF2E2922),
        primaryAction = SurfaceLook(fill = solid(Color(0xFF2E2922)), shadows = listOf(Shadow(Color(0x33000000), 0.dp, 2.dp, 4.dp))),
        primaryActionText = Color(0xFFFCF8F1),
        corners = Corners(12.dp), heroCorners = Corners(14.dp), amountWeight = FontWeight.Bold,
    )
}

private fun wood(): HomeLook {
    val plank = SurfaceLook(
        fill = v(Color(0xFF8F5B39), Color(0xFF6E4127)),
        border = v(Color(0x66FFD9B0), Color(0x88000000)),
        shadows = listOf(Shadow(Color(0xAA1A0C04), 0.dp, 8.dp, 16.dp)),
        inner = listOf(Shadow(Color(0x33FFE0BD), 0.dp, 2.dp, 2.dp)),
        texture = Texture.WOOD,
    )
    return HomeLook(
        style = HomeStyle.WOOD, isDark = true,
        background = v(Color(0xFF3E2618), Color(0xFF24150C)),
        backgroundTexture = Texture.WOOD,
        text = Color(0xFFFFF2E2), muted = Color(0xFFD9BFA4), accent = Color(0xFFF2C14E),
        positive = Color(0xFFB8E28A), warning = Color(0xFFF5C46A),
        hero = plank.copy(fill = v(Color(0xFFB57A4D), Color(0xFF8E5732)), gloss = gloss(0.12f)),
        heroText = Color(0xFFFFF6EA), heroMuted = Color(0xFFF1D8BC),
        ringFill = Color(0xFFF2C14E), ringTrack = Color(0x44000000),
        card = plank,
        action = SurfaceLook(fill = v(Color(0xFF5C3620), Color(0xFF452717)), border = v(Color(0x55FFD9B0), Color(0x99000000)), inner = listOf(Shadow(Color(0x66000000), 0.dp, 2.dp, 4.dp)), texture = Texture.WOOD),
        actionText = Color(0xFFFFF2E2),
        primaryAction = SurfaceLook(fill = v(Color(0xFFF7D27A), Color(0xFFC9962F)), border = v(Color(0xFFFFF0C2), Color(0xFF6B4A0E)), shadows = listOf(Shadow(Color(0x99000000), 0.dp, 3.dp, 6.dp)), gloss = topGloss),
        primaryActionText = Color(0xFF3A2504),
        corners = Corners(18.dp), heroCorners = Corners(22.dp),
    )
}

private fun bio(): HomeLook {
    val membrane = SurfaceLook(
        fill = v(Color(0x33B6FFCF), Color(0x1A3DDC84)),
        border = d(Color(0x99D9FFE6), Color(0x1AFFFFFF), Color(0x66A3E635)),
        borderWidth = 1.2.dp,
        shadows = listOf(Shadow(Color(0x55052E1B), 0.dp, 10.dp, 22.dp)),
        gloss = gloss(0.16f),
        texture = Texture.BUBBLES,
    )
    return HomeLook(
        style = HomeStyle.BIO, isDark = true,
        background = v(Color(0xFF123A2A), Color(0xFF0A2318)),
        backgroundGlows = listOf(Glow(Color(0xFF7BE36A), 0.85f, 0.05f, 0.5f), Glow(Color(0xFF2FB98A), 0.05f, 0.45f, 0.5f), Glow(Color(0xFFC4F06A), 0.9f, 0.85f, 0.4f)),
        text = Color(0xFFEAFFF1), muted = Color(0xFFA9D6BA), accent = Color(0xFFB5F05A),
        positive = Color(0xFFB5F05A), warning = Color(0xFFFFD36B),
        hero = membrane.copy(fill = v(Color(0x5594F0A8), Color(0x332FB98A)), border = d(Color(0xCCE8FFEE), Color(0x22FFFFFF), Color(0x88B5F05A))),
        heroText = Color.White, heroMuted = Color(0xFFCBEFD7),
        ringFill = Color(0xFFB5F05A), ringTrack = Color(0x33FFFFFF),
        card = membrane,
        action = SurfaceLook(fill = solid(Color(0x2ED9FFE6)), border = solid(Color(0x66D9FFE6))),
        actionText = Color.White,
        primaryAction = SurfaceLook(fill = v(Color(0xFFD2FA8A), Color(0xFF9FDB3E)), shadows = listOf(Shadow(Color(0x66B5F05A), 0.dp, 4.dp, 14.dp))),
        primaryActionText = Color(0xFF14310A),
        corners = Corners(34.dp, 18.dp, 38.dp, 22.dp), heroCorners = Corners(44.dp, 26.dp, 48.dp, 30.dp),
    )
}

private fun liquid(): HomeLook {
    val drop = SurfaceLook(
        fill = v(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f)),
        border = v(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.35f)),
        borderWidth = 1.5.dp,
        shadows = listOf(Shadow(Color(0x55001A4D), 0.dp, 14.dp, 26.dp)),
        inner = listOf(Shadow(Color(0x66FFFFFF), 0.dp, 2.dp, 6.dp), Shadow(Color(0x33001A4D), 0.dp, (-6).dp, 12.dp)),
        gloss = gloss(0.28f),
    )
    return HomeLook(
        style = HomeStyle.LIQUID, isDark = true,
        background = v(Color(0xFF0A2A66), Color(0xFF041435)),
        backgroundGlows = listOf(Glow(Color(0xFF3FA9F5), 0.1f, 0.1f, 0.6f), Glow(Color(0xFF00D2FF), 0.95f, 0.4f, 0.5f), Glow(Color(0xFF1E5BFF), 0.2f, 0.9f, 0.6f)),
        text = Color.White, muted = Color(0xFFB9D3F2), accent = Color(0xFF8FDBFF),
        positive = Color(0xFF8FF7C8), warning = Color(0xFFFFD58A),
        hero = drop.copy(
            fill = d(Color(0xFF58B8FF), Color(0xFF1F6FE0), Color(0xFF0B4CC2)),
            glows = listOf(Glow(Color(0xFFBDE8FF), 0.15f, 0.1f, 0.5f), Glow(Color(0xFF00D2FF), 0.9f, 0.9f, 0.6f)),
        ),
        heroText = Color.White, heroMuted = Color(0xFFEAF5FF),
        ringFill = Color.White, ringTrack = Color.White.copy(alpha = 0.25f),
        card = drop,
        action = drop.copy(shadows = emptyList(), inner = emptyList()),
        actionText = Color.White,
        primaryAction = SurfaceLook(fill = v(Color.White, Color(0xFFDDEEFF)), gloss = topGloss, shadows = listOf(Shadow(Color(0x55001A4D), 0.dp, 6.dp, 14.dp))),
        primaryActionText = Color(0xFF0B2E6B),
        corners = Corners(30.dp), heroCorners = Corners(38.dp),
    )
}

private fun ai(): HomeLook {
    val neon = SurfaceLook(
        fill = v(Color(0xFF15122E), Color(0xFF0E0B22)),
        border = d(Color(0xFF22D3EE), Color(0xFF8B5CF6), Color(0xFFEC4899)),
        borderWidth = 1.5.dp,
        shadows = listOf(Shadow(Color(0x668B5CF6), 0.dp, 0.dp, 18.dp)),
    )
    return HomeLook(
        style = HomeStyle.AI, isDark = true,
        background = v(Color(0xFF0B0920), Color(0xFF05040F)),
        backgroundGlows = listOf(Glow(Color(0xFF6D28D9), 0.9f, 0.05f, 0.55f), Glow(Color(0xFF0891B2), 0.05f, 0.5f, 0.5f), Glow(Color(0xFFDB2777), 0.85f, 0.9f, 0.45f)),
        backgroundTexture = Texture.SPARKLES,
        text = Color(0xFFF1F0FF), muted = Color(0xFFA9A6D6), accent = Color(0xFF67E8F9),
        positive = Color(0xFF6EF5C2), warning = Color(0xFFFFCF70),
        hero = neon.copy(
            fill = v(Color(0xFF1D1745), Color(0xFF120E30)),
            borderWidth = 2.dp,
            shadows = listOf(Shadow(Color(0xAA8B5CF6), 0.dp, 0.dp, 28.dp)),
            glows = listOf(Glow(Color(0xFF8B5CF6), 0.9f, 0.0f, 0.7f), Glow(Color(0xFF22D3EE), 0.0f, 1f, 0.6f)),
            texture = Texture.SPARKLES,
        ),
        heroText = Color.White, heroMuted = Color(0xFFC4C1F0),
        ringFill = Color(0xFF67E8F9), ringTrack = Color(0x33FFFFFF),
        card = neon,
        action = SurfaceLook(fill = solid(Color(0xFF1E1A42)), border = d(Color(0x9922D3EE), Color(0x99EC4899))),
        actionText = Color(0xFFF1F0FF),
        primaryAction = SurfaceLook(fill = d(Color(0xFF22D3EE), Color(0xFF8B5CF6), Color(0xFFEC4899)), shadows = listOf(Shadow(Color(0x998B5CF6), 0.dp, 0.dp, 16.dp))),
        primaryActionText = Color.White,
        corners = Corners(22.dp), heroCorners = Corners(28.dp),
    )
}
