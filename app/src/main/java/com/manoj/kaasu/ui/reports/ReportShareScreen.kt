package com.manoj.kaasu.ui.reports

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.domain.WeeklyReport
import com.manoj.kaasu.domain.WeeklyReportUseCase
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.components.CategoryBars
import com.manoj.kaasu.ui.components.DailyBars
import com.manoj.kaasu.ui.components.MealGrid
import com.manoj.kaasu.ui.components.PrimaryButton
import com.manoj.kaasu.ui.components.ScoreRing
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme
import com.manoj.kaasu.ui.util.rememberLockManager
import com.manoj.kaasu.ui.util.shareImage
import com.manoj.kaasu.ui.util.sharedCacheDir
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class ReportShareState(val report: WeeklyReport? = null, val userName: String = "", val recipient: String = "Dad")

@HiltViewModel
class ReportShareViewModel @Inject constructor(
    savedState: SavedStateHandle,
    reports: WeeklyReportUseCase,
) : ViewModel() {
    private val weekStart: LocalDate = LocalDate.ofEpochDay(savedState.get<Long>("week") ?: Dates.weekStart(Dates.today()).toEpochDay())

    val state: StateFlow<ReportShareState> = reports(weekStart)
        .map { ReportShareState(it.report, it.settings.userName, it.settings.reportRecipient.ifBlank { "Dad" }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportShareState())
}

@Composable
fun ReportShareScreen(navController: NavHostController, vm: ReportShareViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lockManager = rememberLockManager()
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        BackTopBar(title = "Send to ${state.recipient}", onBack = { navController.popBackStack() })
        val report = state.report
        if (report == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "This picture is what ${state.recipient} will get.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            ) {
                // The shared image always uses the light theme so it reads well in WhatsApp.
                KaasuTheme(darkTheme = false) {
                    ReportCard(report, state.userName)
                }
            }
        }
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            PrimaryButton(
                text = "Share picture",
                icon = Icons.Rounded.IosShare,
                loading = sharing,
                onClick = {
                    sharing = true
                    scope.launch {
                        val file = runCatching {
                            val bitmap = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                            withContext(Dispatchers.IO) {
                                File(context.sharedCacheDir(), "kaasu-report-${report.weekStart}.png").also { f ->
                                    f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                }
                            }
                        }.getOrNull()
                        sharing = false
                        if (file != null) {
                            lockManager.markExternalLaunch()
                            context.shareImage(
                                file,
                                title = "Send report",
                                caption = "My Kaasu report for ${Dates.weekRangeLabel(report.weekStart)}: ${report.total}/100 (${report.grade})",
                            )
                        }
                    }
                },
            )
        }
    }
}

/** The shareable report card. Fixed width so the picture looks the same every time. */
@Composable
fun ReportCard(report: WeeklyReport, userName: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .width(360.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Weekly report", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(userName.ifBlank { "My week" }, style = MaterialTheme.typography.titleLarge)
                    Text(Dates.weekRangeLabel(report.weekStart), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ScoreRing(report.total, report.grade, size = 88.dp)
            }
            Text(report.headline, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Spent", Money.format(report.spendPaise), Modifier.weight(1f))
                Stat("Last week", Money.format(report.lastWeekSpendPaise), Modifier.weight(1f))
                Stat("Lent", Money.format(report.lentPaise), Modifier.weight(1f))
            }
            Section("Marks") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { report.parts.forEach { MarkRow(it) } }
            }
            Section("Day by day") { DailyBars(report.days, chartHeight = 120.dp) }
            if (report.categories.isNotEmpty()) {
                Section("Where it went") { CategoryBars(report.categories, maxRows = 5) }
            }
            Section("Meals") { MealGrid(report.days) }
            if (report.tips.isNotEmpty()) {
                Section("Working on") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        report.tips.take(3).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            Text(
                "Made with Kaasu · ${Dates.dayMonth(Dates.today())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .padding(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = AmountStyles.small, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
