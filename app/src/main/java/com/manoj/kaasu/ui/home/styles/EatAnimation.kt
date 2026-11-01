package com.manoj.kaasu.ui.home.styles

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val EAT_MS = 1100

/** Times (0..1) at which each bite lands, and where on the edge it is taken. */
private val bites = listOf(0.10f to -40f, 0.32f to 35f, 0.54f to 160f)

/**
 * "I ate this": a round snack takes three bites, crumbs fall from each bite, the last crumb
 * vanishes and a tick springs in. Plays once, then calls [onFinished].
 */
@Composable
fun EatAnimation(food: Color, crumb: Color, tick: Color, size: Dp = 28.dp, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (ValueAnimator.areAnimatorsEnabled()) progress.animateTo(1f, tween(EAT_MS, easing = LinearEasing))
        onFinished()
    }
    Canvas(
        Modifier
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val p = progress.value
        val c = center
        val r = min(this.size.width, this.size.height) * 0.36f
        // The snack shrinks away after the last bite.
        val vanish = ((p - 0.72f) / 0.14f).coerceIn(0f, 1f)
        val foodScale = 1f - vanish
        if (foodScale > 0f) {
            drawCircle(food, r * foodScale, c)
            drawCircle(Color.White.copy(alpha = 0.25f), r * foodScale * 0.45f, Offset(c.x - r * 0.3f, c.y - r * 0.3f))
            bites.forEach { (at, angleDeg) ->
                val grow = ((p - at) / 0.08f).coerceIn(0f, 1f)
                if (grow > 0f) {
                    val a = Math.toRadians(angleDeg.toDouble())
                    val bc = Offset(c.x + cos(a).toFloat() * r * 0.95f * foodScale, c.y + sin(a).toFloat() * r * 0.95f * foodScale)
                    drawCircle(Color.Black, r * 0.5f * grow * foodScale, bc, blendMode = BlendMode.Clear)
                }
            }
        }
        // Crumbs: each bite throws a few that arc out and fall.
        bites.forEachIndexed { bi, (at, angleDeg) ->
            val life = ((p - at) / 0.35f).coerceIn(0f, 1f)
            if (life <= 0f || life >= 1f) return@forEachIndexed
            val a = Math.toRadians(angleDeg.toDouble())
            repeat(5) { k ->
                val spread = (k - 2) * 0.35f
                val dir = a + spread
                val dist = r * (0.6f + life * 1.3f)
                val fall = life * life * r * 1.4f
                val pos = Offset(c.x + cos(dir).toFloat() * dist, c.y + sin(dir).toFloat() * dist + fall)
                drawCircle(crumb.copy(alpha = 1f - life), r * (0.11f - k * 0.008f + bi * 0.005f), pos)
            }
        }
        // Tick springs in at the end (slight overshoot).
        val t = ((p - 0.8f) / 0.2f).coerceIn(0f, 1f)
        if (t > 0f) {
            val s = 1f + 0.18f * sin(t * Math.PI.toFloat())
            val tickPath = Path().apply {
                moveTo(c.x - r * 0.62f * s, c.y + r * 0.02f)
                lineTo(c.x - r * 0.15f * s, c.y + r * 0.48f * s)
                lineTo(c.x + r * 0.7f * s, c.y - r * 0.42f * s)
            }
            drawPath(tickPath, tick.copy(alpha = t), style = Stroke(width = r * 0.3f, cap = StrokeCap.Round))
        }
    }
}
