package com.manoj.kaasu.ui.home.styles

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Apple-like press: sinks the instant a finger lands, springs back on release (stiff, slight overshoot). */
private val PressDown = spring<Float>(dampingRatio = 1f, stiffness = 2200f)
private val PressUp = spring<Float>(dampingRatio = 0.55f, stiffness = 900f)

/**
 * Click with a visible press. The action fires just after the spring-back starts (about 70 ms), so the
 * button is seen to react before the next page slides in, without ever making navigation feel slow.
 */
fun Modifier.pressClick(
    enabled: Boolean = true,
    role: Role = Role.Button,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(pressed) { scale.animateTo(if (pressed) pressedScale else 1f, if (pressed) PressDown else PressUp) }
    this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role) {
            if (!motion) {
                onClick()
            } else {
                scope.launch {
                    // A tap can be shorter than one frame; make sure the dip is still seen.
                    if (scale.value > pressedScale + 0.02f) scale.animateTo(pressedScale, PressDown)
                    launch { scale.animateTo(1f, PressUp) }
                    delay(70)
                    onClick()
                }
            }
        }
}
