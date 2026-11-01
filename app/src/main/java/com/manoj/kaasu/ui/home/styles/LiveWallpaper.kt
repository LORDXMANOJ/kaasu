package com.manoj.kaasu.ui.home.styles

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The live wallpaper behind every screen. The static layer (base gradient and texture) is rendered once
 * into a bitmap; only the light layer (drifting glows and particles) is drawn per frame, in the draw phase.
 * Freezes on a still frame when the phone's animations are turned off.
 */
@Composable
fun AppBackdrop(look: HomeLook, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val bitmap = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                CanvasDrawScope().draw(this, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap), size) {
                    drawRect(look.background)
                    drawStaticTexture(look.backgroundTexture)
                }
                onDrawBehind { drawImage(bitmap) }
            },
    ) {
        LiveLayer(look)
    }
}

private class Particle(val x: Float, val speed: Float, val size: Float, val phase: Float, val tint: Int)

@Composable
private fun LiveLayer(look: HomeLook) {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    var seconds by remember { mutableFloatStateOf(14f) }
    if (animate && look.wallpaper != Wallpaper.NONE) {
        LaunchedEffect(look.style) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { seconds = 14f + (it - start) / 1_000_000_000f }
        }
    }
    val particles = remember(look.style) {
        val rnd = Random(look.style.ordinal * 31 + 7)
        List(44) { Particle(rnd.nextFloat(), 0.018f + rnd.nextFloat() * 0.04f, 0.8f + rnd.nextFloat() * 2.2f, rnd.nextFloat(), rnd.nextInt(4)) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = seconds
        drawDriftingGlows(look.backgroundGlows, t)
        when (look.wallpaper) {
            Wallpaper.EMBERS -> drawEmbers(t, particles)
            Wallpaper.BUBBLES -> drawBubbles(t, particles, look.accent)
            Wallpaper.SPARKLES -> drawSparkles(t, particles, look.accent)
            Wallpaper.SHEEN -> drawSheen(t, look.isDark)
            Wallpaper.DRIFT, Wallpaper.NONE -> Unit
        }
    }
}

/** The style's colour lights, each wandering slowly on its own small orbit. */
private fun DrawScope.drawDriftingGlows(glows: List<Glow>, t: Float) {
    val longest = maxOf(size.width, size.height)
    glows.forEachIndexed { i, g ->
        val speed = 0.035f + i * 0.012f
        val cx = (g.x + 0.08f * sin(t * speed + i * 1.7f)) * size.width
        val cy = (g.y + 0.06f * cos(t * speed * 0.8f + i * 2.3f)) * size.height
        val r = longest * g.radius * (1f + 0.06f * sin(t * 0.05f + i))
        drawCircle(
            Brush.radialGradient(listOf(g.color.copy(alpha = g.color.alpha * 0.8f), g.color.copy(alpha = g.color.alpha * 0.3f), Color.Transparent), Offset(cx, cy), r),
            r,
            Offset(cx, cy),
        )
    }
}

private val emberColors = listOf(Color(0xFFFF7A1A), Color(0xFFFF9A3C), Color(0xFFFF453A), Color(0xFFFFB25C))

private fun DrawScope.drawEmbers(t: Float, list: List<Particle>) {
    val w = size.width
    val h = size.height
    glow(Color(0xFFFF6A12), Offset(w * (0.3f + 0.18f * sin(t * 0.07f)), h * 1.04f), w * 0.95f, 0.24f)
    glow(Color(0xFFE5251B), Offset(w * (0.78f + 0.12f * cos(t * 0.05f)), h * 0.98f), w * 0.7f, 0.15f)
    glow(Color(0xFFFF7A1A), Offset(w * 0.92f, h * 0.02f), w * 0.45f, 0.05f)
    list.forEach { e ->
        val travel = h * 1.15f
        val y = h * 1.05f - ((t * e.speed * h + e.phase * travel) % travel)
        val x = e.x * w + sin(t * 0.5f + e.phase * 6.28f) * 14f * density
        val alpha = ((y / h).coerceIn(0f, 1f) * 0.9f + 0.05f) * (0.65f + 0.35f * sin(t * 2.6f + e.phase * 19f))
        val r = e.size * density
        val c = emberColors[e.tint]
        drawCircle(c.copy(alpha = alpha * 0.18f), r * 3.2f, Offset(x, y))
        drawCircle(c.copy(alpha = alpha), r, Offset(x, y))
    }
}

private fun DrawScope.drawBubbles(t: Float, list: List<Particle>, accent: Color) {
    val w = size.width
    val h = size.height
    list.take(18).forEach { b ->
        val travel = h * 1.2f
        val y = h * 1.1f - ((t * b.speed * 0.6f * h + b.phase * travel) % travel)
        val x = b.x * w + sin(t * 0.7f + b.phase * 6.28f) * 18f * density
        val r = (3f + b.size * 4f) * density
        val fade = (y / h).coerceIn(0f, 1f)
        drawCircle(Color.White.copy(alpha = 0.05f * fade), r, Offset(x, y))
        drawCircle(accent.copy(alpha = 0.35f * fade), r, Offset(x, y), style = Stroke(1f * density))
        drawCircle(Color.White.copy(alpha = 0.5f * fade), r * 0.22f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
}

private fun DrawScope.drawSparkles(t: Float, list: List<Particle>, accent: Color) {
    val w = size.width
    val h = size.height
    list.forEach { s ->
        val twinkle = ((sin(t * (0.8f + s.speed * 20f) + s.phase * 40f) + 1f) / 2f).let { it * it }
        if (twinkle < 0.05f) return@forEach
        val c = Offset(s.x * w, ((s.phase * 1.7f + s.x * 0.3f) % 1f) * h)
        val r = (2f + s.size * 2.2f) * density * (0.6f + 0.4f * twinkle)
        val color = (if (s.tint % 2 == 0) accent else Color.White).copy(alpha = 0.75f * twinkle)
        drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 1.2f * density, cap = StrokeCap.Round)
        drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 1.2f * density, cap = StrokeCap.Round)
    }
}

/** A soft band of light that crosses the material every ~20 s, like a lamp moving over it. */
private fun DrawScope.drawSheen(t: Float, dark: Boolean) {
    val w = size.width
    val h = size.height
    val p = ((t % 22f) / 22f) * 2.4f - 0.7f
    val alpha = if (dark) 0.07f else 0.22f
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
            start = Offset(w * (p - 0.35f), 0f),
            end = Offset(w * (p + 0.35f), h * 0.6f),
        ),
    )
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.4f), Color.Transparent), center, radius), radius, center)
}

/** Paper fibres, wood grain, brushed metal or grain, drawn once into the cached bitmap. */
private fun DrawScope.drawStaticTexture(texture: Texture) {
    if (texture == Texture.NONE) return
    val rnd = Random(11)
    val w = size.width
    val h = size.height
    when (texture) {
        Texture.PAPER -> repeat((w * h / 200f).toInt().coerceAtMost(9000)) {
            drawCircle((if (rnd.nextBoolean()) Color(0xFF5B4630) else Color.White).copy(alpha = rnd.nextFloat() * 0.06f), 0.6f + rnd.nextFloat() * 1.2f, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
        }
        Texture.WOOD -> {
            var y = -4f
            while (y < h + 8f) {
                val amp = 1.5f + rnd.nextFloat() * 4f
                val freq = 0.004f + rnd.nextFloat() * 0.01f
                val phase = rnd.nextFloat() * 6.28f
                val grain = Path().apply {
                    moveTo(0f, y + sin(phase) * amp)
                    var x = 0f
                    while (x <= w) { lineTo(x, y + sin(x * freq + phase) * amp); x += 6f }
                }
                drawPath(grain, (if (rnd.nextFloat() < 0.6f) Color(0xFF1A0B04) else Color(0xFFFFD9AE)).copy(alpha = 0.04f + rnd.nextFloat() * 0.1f), style = Stroke(0.8f + rnd.nextFloat() * 1.8f))
                y += 3f + rnd.nextFloat() * 6f
            }
        }
        Texture.BRUSHED -> {
            var y = 0f
            while (y < h) {
                drawLine((if (rnd.nextBoolean()) Color.White else Color.Black).copy(alpha = rnd.nextFloat() * 0.06f), Offset(0f, y), Offset(w, y), strokeWidth = 0.7f + rnd.nextFloat() * 0.8f)
                y += 1.6f + rnd.nextFloat() * 1.4f
            }
        }
        Texture.GRAIN -> repeat((w * h / 240f).toInt().coerceAtMost(9000)) {
            drawCircle((if (rnd.nextBoolean()) Color.Black else Color.White).copy(alpha = rnd.nextFloat() * 0.05f), 0.5f + rnd.nextFloat() * 0.9f, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
        }
        else -> Unit
    }
}
