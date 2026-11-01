package com.manoj.kaasu.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.core.TimeSlot
import com.manoj.kaasu.data.db.MealType
import com.manoj.kaasu.domain.CategoryStat
import com.manoj.kaasu.domain.DayStat
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme
import kotlin.math.max
import kotlin.math.roundToInt

private val RingSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow)

/** 270° gauge used for today's budget. */
@Composable
fun BudgetRing(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 136.dp,
    stroke: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), RingSpring, label = "budgetRing")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = stroke.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(trackColor, 135f, 270f, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(progressColor, 135f, 270f * animated, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun ScoreRing(score: Int, grade: String, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val colors = KaasuTheme.colors
    val color = when {
        score >= 80 -> colors.success
        score >= 55 -> colors.warning
        else -> colors.danger
    }
    val animated by animateFloatAsState(score / 100f, RingSpring, label = "scoreRing")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier
            .size(size)
            .semantics(mergeDescendants = true) { contentDescription = "Score $score out of 100, grade $grade" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = 11.dp.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = AmountStyles.large, color = MaterialTheme.colorScheme.onSurface)
            Text(grade, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
fun DailyBars(days: List<DayStat>, modifier: Modifier = Modifier, chartHeight: Dp = 150.dp) {
    val colors = KaasuTheme.colors
    val normal = MaterialTheme.colorScheme.primary
    val future = MaterialTheme.colorScheme.surfaceContainerHighest
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val budget = days.maxOfOrNull { it.budgetPaise } ?: 0L
    val top = max(days.maxOfOrNull { it.spentPaise } ?: 0L, budget).coerceAtLeast(1L) * 1.1f
    val barArea = 0.8f
    val today = Dates.today()
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.Bottom) {
                days.forEach { day ->
                    val fraction = (day.spentPaise / top).coerceIn(0f, 1f)
                    val over = day.spentPaise > day.budgetPaise
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clearAndSetSemantics {
                                contentDescription = "${Dates.weekdayShort(day.date)}: ${Money.format(day.spentPaise)} spent" +
                                    if (over) ", over budget" else ""
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (day.spentPaise > 0) {
                            Text(
                                Money.compact(day.spentPaise),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (over) colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Box(
                            Modifier
                                .padding(horizontal = 7.dp)
                                .fillMaxWidth()
                                .fillMaxHeight((fraction * barArea).coerceAtLeast(0.015f))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(
                                    when {
                                        day.isFuture -> future
                                        over -> colors.danger
                                        else -> normal
                                    },
                                ),
                        )
                    }
                }
            }
            if (budget > 0) {
                Canvas(Modifier.matchParentSize()) {
                    val y = size.height - size.height * barArea * (budget / top)
                    drawLine(
                        color = lineColor.copy(alpha = 0.7f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(
                    Dates.weekdayShort(day.date).take(3),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (day.date == today) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (day.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (budget > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Dashed line = daily budget ${Money.format(budget)} · red bars went over",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CategoryBars(stats: List<CategoryStat>, modifier: Modifier = Modifier, maxRows: Int = 6) {
    val shown = stats.take(maxRows)
    val topShare = shown.maxOfOrNull { it.share }?.coerceAtLeast(0.01f) ?: 1f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        shown.forEach { stat ->
            val name = stat.category?.name ?: "Untagged"
            val color = stat.category?.let { com.manoj.kaasu.ui.theme.categoryColor(it.colorIndex) } ?: KaasuTheme.colors.warning
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(stat.category?.icon ?: "untagged", stat.category?.colorIndex ?: 13, size = 36.dp, tint = color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.format(stat.amountPaise), style = AmountStyles.small)
                        Text(
                            "  ${(stat.share * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((stat.share / topShare).coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(color),
                        )
                    }
                }
            }
        }
    }
}

val SlotColors = mapOf(
    TimeSlot.MORNING to Color(0xFFF59E0B),
    TimeSlot.AFTERNOON to Color(0xFF0EA5E9),
    TimeSlot.EVENING to Color(0xFF8B5CF6),
    TimeSlot.NIGHT to Color(0xFF6366F1),
)

@Composable
fun SlotSplit(split: Map<TimeSlot, Long>, modifier: Modifier = Modifier) {
    val total = split.values.sum().coerceAtLeast(1L)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            TimeSlot.entries.forEach { slot ->
                val value = split[slot] ?: 0L
                if (value > 0) {
                    Box(
                        Modifier
                            .weight(value.toFloat())
                            .fillMaxHeight()
                            .background(SlotColors.getValue(slot)),
                    )
                }
            }
        }
        TimeSlot.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { slot ->
                    val value = split[slot] ?: 0L
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(SlotColors.getValue(slot), CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(slot.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${Money.format(value)} · ${(value * 100 / total)}%",
                                style = AmountStyles.small,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealGrid(days: List<DayStat>, modifier: Modifier = Modifier) {
    val colors = KaasuTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(76.dp))
            days.forEach { day ->
                Text(
                    Dates.weekdayShort(day.date).take(1),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MealType.entries.forEach { meal ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(meal.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(76.dp))
                days.forEach { day ->
                    val eaten = meal in day.meals
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val description = "${meal.label} ${Dates.weekdayShort(day.date)}: " +
                            when {
                                eaten -> "eaten"
                                day.isFuture -> "upcoming"
                                else -> "missed"
                            }
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (eaten) colors.success.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    when {
                                        eaten -> colors.success
                                        day.isFuture -> MaterialTheme.colorScheme.outlineVariant
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    CircleShape,
                                )
                                .clearAndSetSemantics { contentDescription = description },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                eaten -> Icon(Icons.Rounded.Check, null, tint = colors.success, modifier = Modifier.size(16.dp))
                                !day.isFuture -> Icon(Icons.Rounded.Remove, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
