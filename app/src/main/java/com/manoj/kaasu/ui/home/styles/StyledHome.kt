package com.manoj.kaasu.ui.home.styles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.MealType
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.ui.components.BudgetRing
import com.manoj.kaasu.ui.components.CategoryIcon
import com.manoj.kaasu.ui.components.TxnUi
import com.manoj.kaasu.ui.home.HomeState
import com.manoj.kaasu.ui.home.MealMark
import com.manoj.kaasu.ui.navigation.LocalBarInset
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.categoryColor
import com.manoj.kaasu.ui.util.findActivity

/** Everything the styled Home screen can do; the screen owns no logic. */
class StyledHomeActions(
    val onSettings: () -> Unit,
    val onStyles: () -> Unit,
    val onProfile: () -> Unit,
    val onEditBudget: () -> Unit,
    val onScan: () -> Unit,
    val onPeople: () -> Unit,
    val onMoneyIn: () -> Unit,
    val onBalance: () -> Unit,
    val onReports: () -> Unit,
    val onToggleMeal: (MealType) -> Unit,
    val onOwed: () -> Unit,
    val onUntagged: () -> Unit,
    val onFailed: (Long) -> Unit,
    val onSeeAll: () -> Unit,
    val onTxn: (Long) -> Unit,
)

private val Gap = 24.dp

@Composable
fun StyledHome(state: HomeState, look: HomeLook, actions: StyledHomeActions) {
    // Dark status-bar icons on light styles, restored when Home leaves.
    val view = LocalView.current
    DisposableEffect(look.isDark, view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !look.isDark
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    val list = rememberLazyListState()
    // 0 = hero fully visible, 1 = hero scrolled away and its figures docked in the top bar.
    val collapse by remember {
        derivedStateOf {
            val hero = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" }
            when {
                hero == null -> if (list.firstVisibleItemIndex > 1) 1f else 0f
                else -> (-hero.offset / (hero.size * 0.55f)).coerceIn(0f, 1f)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp + LocalBarInset.current),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            item(key = "header") { Header(state, look, actions) }
            item(key = "hero") {
                Box(
                    Modifier
                        .rule(look)
                        .graphicsLayer {
                            // The figures rise and shrink toward the top bar as you scroll.
                            val k = collapse
                            alpha = 1f - k * 0.9f
                            scaleX = 1f - k * 0.12f
                            scaleY = 1f - k * 0.12f
                            translationY = size.height * k * 0.18f
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        },
                ) { Hero(state, look, actions) }
            }
            state.lastFailed?.let { failed ->
                item(key = "failed") {
                    NoticeRow(look, Icons.Rounded.ErrorOutline, "Last payment didn't go through", "${Money.format(failed.amountPaise)} to ${failed.title}", Modifier.rule(look)) {
                        actions.onFailed(failed.id)
                    }
                }
            }
            if (state.untaggedCount > 0) {
                item(key = "untagged") {
                    NoticeRow(
                        look,
                        Icons.Rounded.Sell,
                        if (state.untaggedCount == 1) "1 payment needs a tag" else "${state.untaggedCount} payments need a tag",
                        "Paid from another app",
                        Modifier.rule(look),
                        actions.onUntagged,
                    )
                }
            }
            item(key = "figures") { Box(Modifier.rule(look)) { Figures(state, look, actions) } }
            item(key = "meals") { Box(Modifier.rule(look)) { Meals(state, look, actions) } }
            if (state.owedToYouPaise > 0) {
                item(key = "owed") {
                    NoticeRow(
                        look,
                        Icons.Rounded.Group,
                        "Friends owe you ${Money.format(state.owedToYouPaise)}",
                        if (state.peopleOwing == 1) "1 person" else "${state.peopleOwing} people",
                        Modifier.rule(look),
                        actions.onOwed,
                    )
                }
            }
            item(key = "recentHeader") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, color = look.text, modifier = Modifier.weight(1f).semantics { heading() })
                    if (state.recent.isNotEmpty()) {
                        Text(
                            "See all",
                            style = MaterialTheme.typography.labelLarge,
                            color = look.accent,
                            modifier = Modifier
                                .pressClick(onClick = actions.onSeeAll)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (state.recent.isEmpty() && !state.loading) {
                item(key = "empty") {
                    Column {
                        Text("No payments yet", style = MaterialTheme.typography.titleSmall, color = look.text)
                        Text("Scan a QR to make your first one", style = MaterialTheme.typography.bodySmall, color = look.muted)
                    }
                }
            }
            itemsIndexed(state.recent, key = { _, t -> "t${t.id}" }) { index, txn ->
                TxnRow(txn, look, if (index < state.recent.lastIndex) Modifier.rule(look) else Modifier) { actions.onTxn(txn.id) }
            }
        }
        CollapsedBar(state, look, collapse)
    }
}

/** A hairline in the middle of the gap below a section, in place of any card. */
private fun Modifier.rule(look: HomeLook, gap: Dp = Gap): Modifier = drawBehind {
    val y = size.height + gap.toPx() / 2
    drawLine(look.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

/** The docked header that takes over the balance and score once the hero has scrolled away. */
@Composable
private fun CollapsedBar(state: HomeState, look: HomeLook, collapse: Float) {
    if (collapse <= 0.01f) return
    val over = state.leftPaise < 0
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = ((collapse - 0.35f) / 0.65f).coerceIn(0f, 1f)
                translationY = -(1f - collapse) * 24.dp.toPx()
            }
            .drawBehind {
                drawRect(look.background)
                drawLine(look.line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BudgetRing(progress = state.progress, progressColor = if (over) look.warning else look.ringFill, trackColor = look.ringTrack, size = 34.dp, stroke = 4.dp) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    Money.format(if (over) -state.leftPaise else state.leftPaise),
                    style = AmountStyles.large.copy(fontSize = AmountStyles.row.fontSize * 1.25f),
                    color = if (over) look.warning else look.text,
                )
                Text(if (over) "over today" else "left today", style = MaterialTheme.typography.labelSmall, color = look.muted)
            }
            state.balance?.let { b ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(b.amountPaise), style = AmountStyles.row, color = look.text)
                    Text("balance", style = MaterialTheme.typography.labelSmall, color = look.muted)
                }
            }
            if (state.weekHasData) {
                Spacer(Modifier.width(20.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${state.weekScore}", style = AmountStyles.row, color = look.accent)
                    Text("score", style = MaterialTheme.typography.labelSmall, color = look.muted)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(look: HomeLook, icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .pressClick(pressedScale = 0.88f, onClick = onClick)
            .styledSurface(look.chip, Corners(22.dp))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = look.chipText, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun Header(state: HomeState, look: HomeLook, actions: StyledHomeActions) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val initial = state.userName.trim().firstOrNull()?.uppercase() ?: "K"
        Box(
            Modifier
                .size(44.dp)
                .pressClick(pressedScale = 0.88f, onClick = actions.onProfile)
                .styledSurface(look.primaryAction, Corners(22.dp))
                .semantics { contentDescription = "Your profile" },
            contentAlignment = Alignment.Center,
        ) { Text(initial, style = MaterialTheme.typography.titleMedium, color = look.primaryActionText) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state.greeting, style = MaterialTheme.typography.bodySmall, color = look.muted)
            Text(
                state.userName.ifBlank { "Kaasu" },
                style = MaterialTheme.typography.titleLarge,
                color = look.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        RoundButton(look, Icons.Rounded.Palette, "Change style", actions.onStyles)
        Spacer(Modifier.width(8.dp))
        RoundButton(look, Icons.Rounded.Settings, "Settings", actions.onSettings)
    }
}

@Composable
private fun Hero(state: HomeState, look: HomeLook, actions: StyledHomeActions) {
    val over = state.leftPaise < 0
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (over) "Over today's budget" else "Left today", style = MaterialTheme.typography.labelLarge, color = look.muted)
                Text(
                    Money.format(if (over) -state.leftPaise else state.leftPaise),
                    style = AmountStyles.display,
                    color = if (over) look.warning else look.text,
                    maxLines = 1,
                )
                Row(
                    Modifier
                        .pressClick(onClick = actions.onEditBudget)
                        .padding(vertical = 4.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "Daily budget ${Money.format(state.budgetPaise)}. Tap to change" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Spent ${Money.format(state.spentPaise)} of ${Money.format(state.budgetPaise)}", style = MaterialTheme.typography.bodyMedium, color = look.muted)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = look.muted, modifier = Modifier.size(14.dp))
                }
            }
            BudgetRing(
                progress = state.progress,
                progressColor = if (over) look.warning else look.ringFill,
                trackColor = look.ringTrack,
                size = 80.dp,
                stroke = 6.dp,
            ) {
                Text("${(state.progress * 100).toInt().coerceIn(0, 999)}%", style = AmountStyles.small, color = look.text)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroAction(look, "Scan & pay", Icons.Rounded.QrCodeScanner, primary = true, Modifier.weight(1f), actions.onScan)
            HeroAction(look, "Pay person", Icons.Rounded.Group, primary = false, Modifier.weight(1f), actions.onPeople)
            HeroAction(look, "Money in", Icons.Rounded.Add, primary = false, Modifier.weight(1f), actions.onMoneyIn)
        }
    }
}

@Composable
private fun HeroAction(look: HomeLook, label: String, icon: ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val surface = if (primary) look.primaryAction else look.action
    val content = if (primary) look.primaryActionText else look.actionText
    Column(
        modifier
            .heightIn(min = 64.dp)
            .pressClick(onClick = onClick)
            .styledSurface(surface, Corners(16.dp))
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

@Composable
private fun Figures(state: HomeState, look: HomeLook, actions: StyledHomeActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).pressClick(pressedScale = 0.97f, onClick = actions.onBalance).padding(vertical = 4.dp)) {
            Text("Bank balance", style = MaterialTheme.typography.labelLarge, color = look.muted)
            Spacer(Modifier.height(4.dp))
            val balance = state.balance
            if (balance == null) {
                Text("Check balance", style = MaterialTheme.typography.titleMedium, color = look.accent)
                Text("Tap to check or type it", style = MaterialTheme.typography.bodySmall, color = look.muted)
            } else {
                Text(Money.format(balance.amountPaise), style = AmountStyles.large, color = look.text, maxLines = 1)
                Text(
                    when {
                        balance.isEstimate -> "Estimated, ${Dates.relative(balance.asOf)}"
                        balance.fromSms -> "From bank SMS, ${Dates.relative(balance.asOf)}"
                        else -> "As you entered, ${Dates.relative(balance.asOf)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = look.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(56.dp).drawBehind { drawRect(look.line) })
        Column(Modifier.weight(1f).pressClick(pressedScale = 0.97f, onClick = actions.onReports).padding(vertical = 4.dp)) {
            Text("This week", style = MaterialTheme.typography.labelLarge, color = look.muted)
            Spacer(Modifier.height(4.dp))
            if (state.weekHasData) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.weekScore}", style = AmountStyles.large, color = look.text)
                    Text(" / 100", style = MaterialTheme.typography.bodyMedium, color = look.muted, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("Grade ${state.weekGrade}", style = MaterialTheme.typography.bodySmall, color = look.accent)
            } else {
                Text("Report card", style = MaterialTheme.typography.titleMedium, color = look.accent)
                Text("Marks appear after your first payments", style = MaterialTheme.typography.bodySmall, color = look.muted, maxLines = 2)
            }
        }
    }
}

@Composable
private fun Meals(state: HomeState, look: HomeLook, actions: StyledHomeActions) {
    Column {
        Text("Today's meals", style = MaterialTheme.typography.titleSmall, color = look.text)
        Text("Ate at home? Tap it", style = MaterialTheme.typography.bodySmall, color = look.muted)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MealType.entries.forEach { meal -> MealButton(look, meal, state.meals[meal] ?: MealMark.NONE, Modifier.weight(1f)) { actions.onToggleMeal(meal) } }
        }
    }
}

@Composable
private fun MealButton(look: HomeLook, meal: MealType, mark: MealMark, modifier: Modifier, onToggle: () -> Unit) {
    var eating by remember { mutableStateOf(false) }
    val done = mark != MealMark.NONE
    val (icon, name) = when (meal) {
        MealType.BREAKFAST -> Icons.Rounded.FreeBreakfast to "Breakfast"
        MealType.LUNCH -> Icons.Rounded.LunchDining to "Lunch"
        MealType.DINNER -> Icons.Rounded.DinnerDining to "Dinner"
    }
    val surface = if (done) look.primaryAction else look.chip
    val content = if (done) look.primaryActionText else look.chipText
    Column(
        modifier
            .heightIn(min = 76.dp)
            .pressClick(enabled = mark != MealMark.PAID && !eating, role = Role.Checkbox) {
                // Eating plays first; un-ticking is instant.
                if (mark == MealMark.NONE) eating = true else onToggle()
            }
            .styledSurface(surface, Corners(16.dp))
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name, " + when (mark) {
                    MealMark.PAID -> "paid"
                    MealMark.HOME -> "eaten at home"
                    MealMark.NONE -> "not yet"
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (eating) {
                EatAnimation(food = look.accent, crumb = look.accent, tick = look.accent) {
                    eating = false
                    onToggle()
                }
            } else {
                AnimatedContent(
                    targetState = done,
                    transitionSpec = { (fadeIn() + scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.6f)) togetherWith fadeOut() },
                    label = "meal",
                ) { isDone -> Icon(if (isDone) Icons.Rounded.Check else icon, contentDescription = null, tint = content) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = content)
        Text(
            when {
                eating -> "Eating…"
                mark == MealMark.PAID -> "Paid"
                mark == MealMark.HOME -> "At home"
                else -> "Not yet"
            },
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun NoticeRow(look: HomeLook, icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().pressClick(pressedScale = 0.98f, onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = look.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = look.text)
            Text(message, style = MaterialTheme.typography.bodySmall, color = look.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = look.muted)
    }
}

@Composable
private fun TxnRow(txn: TxnUi, look: HomeLook, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isIn = txn.direction == Direction.IN
    val amountColor = when {
        txn.status == PaymentStatus.FAILED -> look.muted
        isIn -> look.positive
        else -> look.text
    }
    Row(modifier.fillMaxWidth().pressClick(pressedScale = 0.98f, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(icon = txn.icon, colorIndex = txn.colorIndex, size = 40.dp, tint = if (txn.isUntagged) look.accent else categoryColor(txn.colorIndex))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(txn.title, style = MaterialTheme.typography.titleSmall, color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(txn.subtitle, style = MaterialTheme.typography.bodySmall, color = if (txn.isUntagged) look.accent else look.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text((if (isIn) "+" else "-") + Money.format(txn.amountPaise), style = AmountStyles.row, color = amountColor)
            if (txn.status != PaymentStatus.SUCCESS) {
                Text(
                    when (txn.status) {
                        PaymentStatus.PENDING -> "Pending"
                        PaymentStatus.UNCONFIRMED -> "Not confirmed"
                        PaymentStatus.FAILED -> "Failed"
                        PaymentStatus.SUCCESS -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = look.warning,
                )
            }
        }
    }
}

/** Kept for other screens that still want a flat titled group. */
@Composable
fun StyledCard(
    surface: SurfaceLook,
    corners: Corners,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressClick(pressedScale = 0.98f, onClick = onClick) else Modifier)
            .styledSurface(surface, corners)
            .padding(padding),
        content = content,
    )
}

