package com.manoj.kaasu.ui.reports

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.domain.ScorePart
import com.manoj.kaasu.domain.WeeklyReport
import com.manoj.kaasu.domain.WeeklyReportUseCase
import com.manoj.kaasu.ui.components.CategoryBars
import com.manoj.kaasu.ui.components.DailyBars
import com.manoj.kaasu.ui.components.EmptyState
import com.manoj.kaasu.ui.components.KCard
import com.manoj.kaasu.ui.components.MealGrid
import com.manoj.kaasu.ui.components.PrimaryButton
import com.manoj.kaasu.ui.components.ScoreRing
import com.manoj.kaasu.ui.components.SlotSplit
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@Immutable
data class ReportsState(
    val loading: Boolean = true,
    val weekOffset: Int = 0,
    val report: WeeklyReport? = null,
    val recipient: String = "Dad",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(private val reports: WeeklyReportUseCase) : ViewModel() {
    private val offset = MutableStateFlow(0)

    val state: StateFlow<ReportsState> = offset.flatMapLatest { weeks ->
        val start = Dates.weekStart(Dates.today()).plusWeeks(weeks.toLong())
        reports(start).map { ReportsState(false, weeks, it.report, it.settings.reportRecipient.ifBlank { "Dad" }) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState())

    fun previousWeek() = offset.update { it - 1 }
    fun nextWeek() = offset.update { (it + 1).coerceAtMost(0) }
}

@Composable
fun ReportsScreen(navController: NavHostController, vm: ReportsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            "Report card",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(start = 20.dp, top = 16.dp, end = 20.dp)
                .semantics { heading() },
        )
        WeekSwitcher(state, onPrevious = vm::previousWeek, onNext = vm::nextWeek)
        val report = state.report
        if (report == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        AnimatedContent(targetState = report, label = "report", contentKey = { it.weekStart }) { r ->
            ReportBody(r, state.recipient, onShare = { navController.navigate(Routes.reportShare(r.weekStart.toEpochDay())) })
        }
    }
}

@Composable
private fun WeekSwitcher(state: ReportsState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val report = state.report
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous week") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (state.weekOffset) {
                    0 -> "This week"
                    -1 -> "Last week"
                    else -> "${-state.weekOffset} weeks ago"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            if (report != null) {
                Text(Dates.weekRangeLabel(report.weekStart), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onNext, enabled = state.weekOffset < 0) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next week")
        }
    }
}

@Composable
private fun ReportBody(report: WeeklyReport, recipient: String, onShare: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + com.manoj.kaasu.ui.navigation.LocalBarInset.current),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "score") { ScoreSummary(report) }
        if (!report.hasData) {
            item(key = "empty") {
                KCard {
                    EmptyState(Icons.Rounded.Insights, "Nothing this week yet", "Pay with Kaasu and log your meals. Your marks fill in as the week goes on.")
                }
            }
            return@LazyColumn
        }
        item(key = "marks") { MarksCard(report.parts) }
        if (report.tips.isNotEmpty()) item(key = "tips") { TipsCard(report.tips) }
        item(key = "daily") {
            KCard {
                Text("Day by day", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                DailyBars(report.days)
            }
        }
        if (report.categories.isNotEmpty()) {
            item(key = "categories") {
                KCard {
                    Text("Where it went", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(14.dp))
                    CategoryBars(report.categories)
                }
            }
        }
        item(key = "slots") {
            KCard {
                Text("Time of day", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                SlotSplit(report.slotSplit)
            }
        }
        item(key = "meals") {
            KCard {
                Text("Meals", style = MaterialTheme.typography.titleMedium)
                Text("Paid meals and ones you ticked at home", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                MealGrid(report.days)
            }
        }
        item(key = "month") { MonthCard(report) }
        item(key = "share") {
            PrimaryButton(text = "Send to $recipient", icon = Icons.Rounded.IosShare, onClick = onShare)
        }
    }
}

@Composable
private fun ScoreSummary(report: WeeklyReport) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(report.total, report.grade, size = 112.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(report.headline, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Spent ${Money.format(report.spendPaise)}", style = AmountStyles.row)
                val diff = report.spendPaise - report.lastWeekSpendPaise
                if (report.lastWeekSpendPaise > 0) {
                    Text(
                        if (diff <= 0) "${Money.format(-diff)} less than last week" else "${Money.format(diff)} more than last week",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diff <= 0) KaasuTheme.colors.success else KaasuTheme.colors.danger,
                    )
                }
                if (report.lentPaise > 0) {
                    Text("Lent ${Money.format(report.lentPaise)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun MarksCard(parts: List<ScorePart>, modifier: Modifier = Modifier) {
    KCard(modifier) {
        Text("How the marks add up", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            parts.forEach { MarkRow(it) }
        }
    }
}

@Composable
fun MarkRow(part: ScorePart) {
    val colors = KaasuTheme.colors
    val color = when {
        part.fraction >= 0.8f -> colors.success
        part.fraction >= 0.5f -> colors.warning
        else -> colors.danger
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(part.title, style = MaterialTheme.typography.titleSmall)
                Text(part.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${part.score}/${part.max}", style = AmountStyles.row, color = color)
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
                    .fillMaxWidth(part.fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
private fun TipsCard(tips: List<String>) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = KaasuTheme.colors.warning)
            Spacer(Modifier.width(8.dp))
            Text("How to do better", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tips.forEach { tip ->
                Row {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(tip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MonthCard(report: WeeklyReport) {
    KCard {
        Text("This month so far", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(report.monthSpendPaise), style = AmountStyles.large)
            }
            Column(Modifier.weight(1f)) {
                Text("Same days last month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(report.lastMonthSpendPaise), style = AmountStyles.large, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (report.lastMonthSpendPaise > 0) {
            val diff = report.lastMonthSpendPaise - report.monthSpendPaise
            Spacer(Modifier.height(6.dp))
            Text(
                if (diff >= 0) "You've saved ${Money.format(diff)} compared to last month" else "${Money.format(-diff)} more than last month so far",
                style = MaterialTheme.typography.bodyMedium,
                color = if (diff >= 0) KaasuTheme.colors.success else KaasuTheme.colors.danger,
                textAlign = TextAlign.Start,
            )
        }
    }
}
