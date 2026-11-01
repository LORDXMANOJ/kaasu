package com.manoj.kaasu.ui.home.styles

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

fun Corners.toShape(): RoundedCornerShape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

/** Draws a style's surface (outer shadows, fill, glows, texture, inner shadows, gloss, rim) behind the content. */
fun Modifier.styledSurface(look: SurfaceLook, corners: Corners): Modifier = drawBehind { drawSurface(look, corners) }

/** Paints a whole-screen backdrop: base gradient, coloured lights and an optional texture. */
fun Modifier.styledBackground(look: HomeLook): Modifier = drawBehind {
    drawRect(look.background)
    drawGlows(look.backgroundGlows, size)
    drawTexture(look.backgroundTexture, size, Path().apply { addRect(Rect(Offset.Zero, size)) }, seed = 11, strength = 0.7f)
}

private fun DrawScope.shapePath(corners: Corners, inset: Float = 0f): Path {
    fun r(dp: androidx.compose.ui.unit.Dp) = max(0f, dp.toPx() - inset).let { CornerRadius(it, it) }
    return Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(inset, inset, size.width - inset, size.height - inset),
                topLeft = r(corners.topStart),
                topRight = r(corners.topEnd),
                bottomRight = r(corners.bottomEnd),
                bottomLeft = r(corners.bottomStart),
            ),
        )
    }
}

private fun DrawScope.drawSurface(look: SurfaceLook, corners: Corners) {
    val path = shapePath(corners)
    // 1. Outer shadows. The caster is clipped out so translucent glass stays clean inside.
    if (look.shadows.isNotEmpty()) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.clipOutPath(path.asAndroidPath())
            look.shadows.forEach { s ->
                val paint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.BLACK
                    setShadowLayer(s.blur.toPx(), s.dx.toPx(), s.dy.toPx(), s.color.toArgb())
                }
                native.drawPath(path.asAndroidPath(), paint)
            }
            native.restore()
        }
    }
    clipPath(path) {
        // 2. Fill and coloured lights.
        drawRect(look.fill)
        drawGlows(look.glows, size)
        // 3. Texture.
        drawTexture(look.texture, size, path, seed = (size.width * 31 + size.height).toInt())
        // 4. Inner shadows: a frame outside the shape casts its shadow inward.
        if (look.inner.isNotEmpty()) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                look.inner.forEach { s ->
                    val spread = s.blur.toPx() * 3 + 40f
                    val frame = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(-spread, -spread, size.width + spread, size.height + spread))
                        addPath(path)
                    }
                    val paint = Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.BLACK
                        setShadowLayer(s.blur.toPx(), s.dx.toPx(), s.dy.toPx(), s.color.toArgb())
                    }
                    // The frame itself lies outside the clip, so only its shadow lands inside the shape.
                    native.save()
                    native.clipPath(path.asAndroidPath())
                    native.drawPath(frame.asAndroidPath(), paint)
                    native.restore()
                }
            }
        }
        // 5. Gloss on the upper part.
        look.gloss?.let { drawRect(it) }
    }
    // 6. Rim light / border.
    look.border?.let {
        val w = look.borderWidth.toPx()
        drawPath(shapePath(corners, inset = w / 2), it, style = Stroke(width = w))
    }
}

private fun DrawScope.drawGlows(glows: List<Glow>, area: Size) {
    val longest = max(area.width, area.height)
    glows.forEach { g ->
        val center = Offset(area.width * g.x, area.height * g.y)
        val radius = longest * g.radius
        drawCircle(
            brush = Brush.radialGradient(
                listOf(g.color.copy(alpha = g.color.alpha * 0.85f), g.color.copy(alpha = g.color.alpha * 0.35f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

private fun DrawScope.drawTexture(texture: Texture, area: Size, clip: Path, seed: Int, strength: Float = 1f) {
    if (texture == Texture.NONE || area.width <= 0f || area.height <= 0f) return
    val rnd = Random(seed)
    clipRect(0f, 0f, area.width, area.height) {
        when (texture) {
            Texture.PAPER -> {
                repeat((area.width * area.height / 220f).toInt().coerceAtMost(3500)) {
                    val dark = rnd.nextBoolean()
                    drawCircle(
                        color = (if (dark) Color(0xFF5B4630) else Color.White).copy(alpha = rnd.nextFloat() * 0.07f * strength),
                        radius = 0.6f + rnd.nextFloat() * 1.2f,
                        center = Offset(rnd.nextFloat() * area.width, rnd.nextFloat() * area.height),
                    )
                }
                repeat((area.width * area.height / 6000f).toInt().coerceAtMost(220)) {
                    val start = Offset(rnd.nextFloat() * area.width, rnd.nextFloat() * area.height)
                    val len = 6f + rnd.nextFloat() * 16f
                    val angle = rnd.nextFloat() * PI.toFloat()
                    drawLine(
                        Color(0xFF6B5638).copy(alpha = 0.06f * strength),
                        start,
                        Offset(start.x + len * kotlin.math.cos(angle), start.y + len * sin(angle)),
                        strokeWidth = 0.8f,
                    )
                }
            }
            Texture.WOOD -> {
                var y = -4f
                while (y < area.height + 8f) {
                    val amp = 1.5f + rnd.nextFloat() * 4f
                    val freq = 0.004f + rnd.nextFloat() * 0.01f
                    val phase = rnd.nextFloat() * 6.28f
                    val grain = Path()
                    var x = 0f
                    grain.moveTo(0f, y + sin(phase) * amp)
                    while (x <= area.width) {
                        grain.lineTo(x, y + sin(x * freq + phase) * amp)
                        x += 6f
                    }
                    val dark = rnd.nextFloat() < 0.6f
                    drawPath(
                        grain,
                        (if (dark) Color(0xFF2A1308) else Color(0xFFFFD9AE)).copy(alpha = (0.05f + rnd.nextFloat() * 0.13f) * strength),
                        style = Stroke(width = 0.8f + rnd.nextFloat() * 1.8f),
                    )
                    y += 3f + rnd.nextFloat() * 6f
                }
                if (area.width > 200f) {
                    val knot = Offset(area.width * (0.2f + rnd.nextFloat() * 0.6f), area.height * (0.3f + rnd.nextFloat() * 0.4f))
                    repeat(5) { i ->
                        drawOval(
                            Color(0xFF2A1308).copy(alpha = 0.10f * strength),
                            topLeft = Offset(knot.x - 18f - i * 7f, knot.y - 6f - i * 3f),
                            size = Size(36f + i * 14f, 12f + i * 6f),
                            style = Stroke(width = 1.2f),
                        )
                    }
                }
            }
            Texture.BRUSHED -> {
                var y = 0f
                while (y < area.height) {
                    val light = rnd.nextBoolean()
                    drawLine(
                        (if (light) Color.White else Color.Black).copy(alpha = rnd.nextFloat() * 0.07f * strength),
                        Offset(0f, y),
                        Offset(area.width, y),
                        strokeWidth = 0.7f + rnd.nextFloat() * 0.8f,
                    )
                    y += 1.6f + rnd.nextFloat() * 1.4f
                }
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f * strength), Color.Transparent),
                        start = Offset(area.width * 0.2f, 0f),
                        end = Offset(area.width * 0.8f, area.height),
                    ),
                )
            }
            Texture.GRAIN -> repeat((area.width * area.height / 260f).toInt().coerceAtMost(3000)) {
                drawCircle(
                    color = (if (rnd.nextBoolean()) Color.Black else Color.White).copy(alpha = rnd.nextFloat() * 0.06f * strength),
                    radius = 0.5f + rnd.nextFloat() * 0.9f,
                    center = Offset(rnd.nextFloat() * area.width, rnd.nextFloat() * area.height),
                )
            }
            Texture.BUBBLES -> repeat(7) {
                val r = 3f + rnd.nextFloat() * 10f
                val c = Offset(rnd.nextFloat() * area.width, rnd.nextFloat() * area.height)
                drawCircle(Color.White.copy(alpha = 0.10f), r, c)
                drawCircle(Color.White.copy(alpha = 0.45f), r, c, style = Stroke(1f))
                drawCircle(Color.White.copy(alpha = 0.7f), r * 0.25f, Offset(c.x - r * 0.35f, c.y - r * 0.35f))
            }
            Texture.SPARKLES -> repeat(if (area.height > 900f) 26 else 6) {
                val c = Offset(rnd.nextFloat() * area.width, rnd.nextFloat() * area.height)
                val s = 2f + rnd.nextFloat() * 5f
                val color = listOf(Color(0xFF67E8F9), Color(0xFFF0ABFC), Color.White)[rnd.nextInt(3)].copy(alpha = 0.35f + rnd.nextFloat() * 0.5f)
                drawLine(color, Offset(c.x - s, c.y), Offset(c.x + s, c.y), strokeWidth = 1.2f, cap = StrokeCap.Round)
                drawLine(color, Offset(c.x, c.y - s), Offset(c.x, c.y + s), strokeWidth = 1.2f, cap = StrokeCap.Round)
            }
            Texture.NONE -> Unit
        }
    }
}
