package com.manoj.kaasu.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.manoj.kaasu.ui.home.styles.Corners
import com.manoj.kaasu.ui.home.styles.HomeLook
import com.manoj.kaasu.ui.home.styles.styledSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs
import kotlin.math.roundToInt

/** Space the floating tab bar covers at the bottom; top-level screens add it to their bottom padding. */
val LocalBarInset = compositionLocalOf { 0.dp }

/** Height the bar takes above the system navigation area, including its margin. */
val BarReservedHeight: Dp = 96.dp

/** Apple-like motion: quick, a touch of overshoot, settles without wobbling. */
val TabSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Routes.HISTORY, "History", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
    Tab(Routes.PEOPLE, "People", Icons.Outlined.Group, Icons.Rounded.Group),
    Tab(Routes.REPORTS, "Reports", Icons.Outlined.Insights, Icons.Rounded.Insights),
)

fun tabIndex(route: String?): Int = tabs.indexOfFirst { it.route == route }

/**
 * A floating pill with a raised glass "lens" on the selected tab. The lens slides to the tapped tab on a
 * spring and stretches while it travels, then settles. Colours come from the current Home style.
 */
@Composable
fun LiquidTabBar(look: HomeLook, current: String?, onSelect: (String) -> Unit, onScan: () -> Unit, hazeState: HazeState? = null) {
    val selected = tabIndex(current).coerceAtLeast(0)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 8.dp)
            .height(76.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(62.dp),
        ) {
            val density = LocalDensity.current
            val itemWidth = maxWidth / tabs.size
            val itemPx = with(density) { itemWidth.toPx() }
            val x = remember { Animatable(selected * itemPx) }
            LaunchedEffect(selected, itemPx) { x.animateTo(selected * itemPx, TabSpring) }
            // Stretch the lens a little in the direction of travel, like a drop of liquid.
            val velocity = abs(x.velocity)
            val stretch = (velocity / (itemPx * 12f)).coerceIn(0f, 0.22f)

            // Frosted glass: the page behind is blurred (22 dp, 10% over Haze's default), tinted with the style's base.
            val base = MaterialTheme.colorScheme.background
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .styledSurface(look.bar.copy(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Transparent)), Corners(31.dp))
                    .clip(RoundedCornerShape(31.dp))
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = HazeStyle(
                                    backgroundColor = base,
                                    tints = listOf(HazeTint(base.copy(alpha = 0.55f))),
                                    blurRadius = 22.dp,
                                    noiseFactor = 0.08f,
                                ),
                            )
                        } else {
                            Modifier.styledSurface(look.bar, Corners(31.dp))
                        },
                    ),
            )
            // The lens is taller than the bar, so it overflows above and below.
            Box(
                Modifier
                    .offset { IntOffset(x.value.roundToInt(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = 1f + stretch
                        scaleY = 1.16f - stretch * 0.35f
                    }
                    .padding(horizontal = 4.dp)
                    .styledSurface(look.lens, Corners(24.dp)),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                tabs.forEachIndexed { index, tab ->
                    TabButton(look, tab, index == selected, Modifier.width(itemWidth)) { onSelect(tab.route) }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        ScanButton(look, onScan)
    }
}

@Composable
private fun TabButton(look: HomeLook, tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) { scale.animateTo(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }
    Box(
        modifier
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(24.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.Tab
                contentDescription = tab.label
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = { (fadeIn(spring(stiffness = 500f)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.85f)) togetherWith fadeOut(spring(stiffness = 700f)) },
            label = "tab",
        ) { isSelected ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isSelected) tab.selectedIcon else tab.icon,
                    contentDescription = null,
                    tint = if (isSelected) look.lensText else look.barIcon,
                    modifier = Modifier.size(22.dp),
                )
                if (isSelected) {
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = look.lensText, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ScanButton(look: HomeLook, onScan: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) { scale.animateTo(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
    Box(
        Modifier
            .size(62.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .styledSurface(look.primaryAction, Corners(31.dp))
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onScan)
            .semantics { contentDescription = "Scan QR and pay" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = look.primaryActionText, modifier = Modifier.size(28.dp))
    }
}
