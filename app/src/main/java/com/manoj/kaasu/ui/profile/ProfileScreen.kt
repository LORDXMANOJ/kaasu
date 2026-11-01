package com.manoj.kaasu.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.KaasuDatabase
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.prefs.UserSettings
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.PaymentRepository
import com.manoj.kaasu.data.upi.UpiUri
import com.manoj.kaasu.domain.BudgetRules
import com.manoj.kaasu.domain.DebtCalculator
import com.manoj.kaasu.ui.common.AmountDialog
import com.manoj.kaasu.ui.common.ConfirmDialog
import com.manoj.kaasu.ui.common.TextInputDialog
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.home.styles.Corners
import com.manoj.kaasu.ui.home.styles.HomeStyle
import com.manoj.kaasu.ui.home.styles.StylePickerSheet
import com.manoj.kaasu.ui.home.styles.lookFor
import com.manoj.kaasu.ui.home.styles.pressClick
import com.manoj.kaasu.ui.home.styles.styledSurface
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class ProfileState(
    val settings: UserSettings = UserSettings(),
    val spentMonthPaise: Long = 0,
    val receivedMonthPaise: Long = 0,
    val owedToYouPaise: Long = 0,
    val payments: Int = 0,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: SettingsRepository,
    payments: PaymentRepository,
    private val categories: CategoryRepository,
    private val db: KaasuDatabase,
) : ViewModel() {
    val state: StateFlow<ProfileState> = combine(settings.settings, payments.all, categories.categories) { s, all, cats ->
        val map = cats.associateBy { it.id }
        val monthStart = Dates.monthStart(Dates.today())
        val month = all.filter { !Dates.toLocalDate(it.createdAt).isBefore(monthStart) && it.status == PaymentStatus.SUCCESS }
        ProfileState(
            settings = s,
            spentMonthPaise = month.filter { BudgetRules.isSpend(it, map, s.countLentInBudget) }.sumOf { it.amountPaise },
            receivedMonthPaise = month.filter { it.direction == Direction.IN }.sumOf { it.amountPaise },
            owedToYouPaise = DebtCalculator.byContact(all, map).values.sumOf { it.outstandingPaise.coerceAtLeast(0) },
            payments = all.size,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState())

    fun setName(v: String) = viewModelScope.launch { settings.setUserName(v) }
    fun setVpa(v: String) = viewModelScope.launch { settings.setMyVpa(v) }
    fun setBudget(v: Long) = viewModelScope.launch { settings.setDailyBudget(v) }
    fun setStyle(s: HomeStyle) = viewModelScope.launch { settings.setHomeStyle(s.name) }

    /** Deletes every payment, person and setting on this phone; Kaasu opens at setup again. */
    fun resetEverything() = viewModelScope.launch {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        categories.ensureSeeded()
        settings.clearAll()
    }
}

private enum class Edit { NAME, VPA, BUDGET, STYLE, RESET }

@Composable
fun ProfileScreen(navController: NavHostController, vm: ProfileViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state.settings
    val palette = KaasuTheme.palette
    val look = lookFor(HomeStyle.from(s.homeStyle))
    var editing by rememberSaveable { mutableStateOf<Edit?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar(title = "Profile", onBack = { navController.popBackStack() })
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "who") {
                Column(Modifier.fillMaxWidth().rule(palette.line), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(88.dp).styledSurface(look.primaryAction, Corners(44.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(s.userName.trim().firstOrNull()?.uppercase() ?: "K", style = AmountStyles.hero, color = look.primaryActionText)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(s.userName.ifBlank { "Add your name" }, style = MaterialTheme.typography.headlineSmall, color = palette.ink)
                    Text(
                        s.myVpa.ifBlank { "No UPI ID yet" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                    )
                }
            }
            item(key = "month") {
                Column(Modifier.rule(palette.line)) {
                    Text("This month", style = MaterialTheme.typography.labelLarge, color = palette.muted)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Figure("Spent", Money.format(state.spentMonthPaise), palette.ink, Modifier.weight(1f))
                        Figure("Received", Money.format(state.receivedMonthPaise), palette.positive, Modifier.weight(1f))
                        Figure("Owed to you", Money.format(state.owedToYouPaise), palette.accent, Modifier.weight(1f))
                    }
                }
            }
            item(key = "rows") {
                Column(Modifier.rule(palette.line)) {
                    LinkRow(Icons.Rounded.Person, "Your name", s.userName.ifBlank { "Not set" }) { editing = Edit.NAME }
                    LinkRow(Icons.Rounded.AlternateEmail, "Your UPI ID", s.myVpa.ifBlank { "Shown in payback reminders" }) { editing = Edit.VPA }
                    LinkRow(Icons.Rounded.Savings, "Daily budget", Money.format(s.dailyBudgetPaise)) { editing = Edit.BUDGET }
                    LinkRow(Icons.Rounded.Palette, "Style", HomeStyle.from(s.homeStyle).label) { editing = Edit.STYLE }
                    LinkRow(Icons.AutoMirrored.Rounded.Send, "Weekly report goes to", s.reportRecipient) { navController.navigate(Routes.SETTINGS) }
                    LinkRow(Icons.Rounded.Settings, "Settings", "SMS, alerts, PIN, backup") { navController.navigate(Routes.SETTINGS) }
                }
            }
            item(key = "danger") {
                LinkRow(Icons.Rounded.DeleteForever, "Reset Kaasu", "Delete every payment, person and setting", tint = palette.negative) { editing = Edit.RESET }
            }
            item(key = "foot") {
                Text(
                    "${state.payments} payments recorded. Everything stays on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.faint,
                )
            }
        }
    }

    when (editing) {
        Edit.NAME -> TextInputDialog("Your name", s.userName, { vm.setName(it); editing = null }, { editing = null }, allowEmpty = false)
        Edit.VPA -> TextInputDialog(
            title = "Your UPI ID",
            initial = s.myVpa,
            onConfirm = { v -> if (v.isBlank() || UpiUri.isValidVpa(v)) { vm.setVpa(v); editing = null } },
            onDismiss = { editing = null },
            placeholder = "name@okaxis",
        )
        Edit.BUDGET -> AmountDialog(
            title = "Daily budget",
            message = "What you aim to spend each day",
            initialPaise = s.dailyBudgetPaise,
            onConfirm = { vm.setBudget(it); editing = null },
            onDismiss = { editing = null },
        )
        Edit.STYLE -> StylePickerSheet(current = HomeStyle.from(s.homeStyle), onPick = vm::setStyle, onDismiss = { editing = null })
        Edit.RESET -> ConfirmDialog(
            title = "Reset Kaasu?",
            message = "This deletes every payment, person, meal and setting on this phone. It can't be undone. Back up first from Settings if you want a copy.",
            confirmLabel = "Delete everything",
            destructive = true,
            onConfirm = { vm.resetEverything(); editing = null },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

private fun Modifier.rule(line: Color): Modifier = drawBehind {
    val y = size.height + 12.dp.toPx()
    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

@Composable
private fun Figure(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = AmountStyles.row, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = KaasuTheme.palette.muted)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, tint: Color = KaasuTheme.palette.ink, onClick: () -> Unit) {
    val p = KaasuTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressClick(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == p.ink) p.muted else tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = p.faint)
    }
}
