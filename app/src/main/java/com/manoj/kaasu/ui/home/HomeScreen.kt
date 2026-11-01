package com.manoj.kaasu.ui.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import com.manoj.kaasu.ui.util.rememberLockManager
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.remember
import com.manoj.kaasu.ui.home.styles.HomeStyle
import com.manoj.kaasu.ui.home.styles.StyledHome
import com.manoj.kaasu.ui.home.styles.StyledHomeActions
import com.manoj.kaasu.ui.home.styles.StylePickerSheet
import com.manoj.kaasu.ui.home.styles.lookFor
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.MealType
import com.manoj.kaasu.ui.common.AmountDialog
import com.manoj.kaasu.ui.components.BudgetRing
import com.manoj.kaasu.ui.components.EmptyState
import com.manoj.kaasu.ui.components.KCard
import com.manoj.kaasu.ui.components.ScoreRing
import com.manoj.kaasu.ui.components.SectionHeader
import com.manoj.kaasu.ui.components.TxnRow
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.navigateTopLevel
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme

@Composable
fun HomeScreen(navController: NavHostController, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editBudget by rememberSaveable { mutableStateOf(false) }
    var editBalance by rememberSaveable { mutableStateOf(false) }
    var addMoneyIn by rememberSaveable { mutableStateOf(false) }
    var checkBalance by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val lockManager = rememberLockManager()
    var pickStyle by rememberSaveable { mutableStateOf(false) }

        val look = remember(state.homeStyle) { lookFor(state.homeStyle) }
        StyledHome(
            state = state,
            look = look,
            actions = StyledHomeActions(
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onStyles = { pickStyle = true },
                onProfile = { navController.navigate(Routes.PROFILE) },
                onEditBudget = { editBudget = true },
                onScan = { navController.navigate(Routes.scan()) },
                onPeople = { navController.navigate(Routes.PICK_PERSON) },
                onMoneyIn = { addMoneyIn = true },
                onBalance = { checkBalance = true },
                onReports = { navController.navigateTopLevel(Routes.REPORTS) },
                onToggleMeal = { vm.onEvent(HomeEvent.ToggleMeal(it)) },
                onOwed = { navController.navigateTopLevel(Routes.PEOPLE) },
                onUntagged = { navController.navigate(Routes.UNTAGGED) },
                onFailed = { navController.navigate(Routes.txn(it)) },
                onSeeAll = { navController.navigateTopLevel(Routes.HISTORY) },
                onTxn = { navController.navigate(Routes.txn(it)) },
            ),
        )

    if (pickStyle) {
        StylePickerSheet(
            current = state.homeStyle,
            onPick = { vm.onEvent(HomeEvent.SetHomeStyle(it)) },
            onDismiss = { pickStyle = false },
        )
    }

    if (editBudget) {
        AmountDialog(
            title = "Today's budget",
            message = "Only for today. Your usual budget is ${Money.format(state.defaultBudgetPaise)}.",
            initialPaise = state.budgetPaise,
            onConfirm = {
                vm.onEvent(HomeEvent.SetTodayBudget(it))
                editBudget = false
            },
            onDismiss = { editBudget = false },
            onClear = if (state.hasTodayOverride) {
                {
                    vm.onEvent(HomeEvent.SetTodayBudget(null))
                    editBudget = false
                }
            } else {
                null
            },
            clearLabel = "Use usual",
        )
    }
    if (checkBalance) {
        CheckBalanceDialog(
            onSbi = {
                checkBalance = false
                lockManager.markExternalLaunch()
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SBI_BALANCE_NUMBER"))) }
            },
            onUpiApp = {
                checkBalance = false
                val pkg = state.defaultUpiPackage ?: "com.phonepe.app"
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    lockManager.markExternalLaunch()
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Couldn't open your UPI app", Toast.LENGTH_SHORT).show()
                }
            },
            onType = {
                checkBalance = false
                editBalance = true
            },
            onDismiss = { checkBalance = false },
        )
    }
    if (addMoneyIn) {
        AmountDialog(
            title = "Money received",
            message = "Added to your balance as money in. Doesn't touch today's spending budget.",
            initialPaise = null,
            onConfirm = {
                vm.onEvent(HomeEvent.AddMoneyIn(it))
                addMoneyIn = false
            },
            onDismiss = { addMoneyIn = false },
        )
    }
    if (editBalance) {
        AmountDialog(
            title = "Bank balance",
            message = "Type what your bank app shows now. Kaasu adjusts it as you pay and get bank SMS.",
            initialPaise = state.balance?.amountPaise,
            onConfirm = {
                vm.onEvent(HomeEvent.SetBalance(it))
                editBalance = false
            },
            onDismiss = { editBalance = false },
        )
    }
}

private const val SBI_BALANCE_NUMBER = "09223766666"

@Composable
private fun CheckBalanceDialog(onSbi: () -> Unit, onUpiApp: () -> Unit, onType: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BalanceOption(
                    Icons.Rounded.Call,
                    "Ask SBI (free missed call)",
                    "Call, let it ring once and hang up. SBI's SMS reply updates Kaasu by itself.",
                    onSbi,
                )
                BalanceOption(Icons.Rounded.Wallet, "Open my UPI app", "Tap Check Balance there, then type it here if you like", onUpiApp)
                BalanceOption(Icons.Rounded.Edit, "Type it myself", "What your bank app shows now", onType)
                Text(
                    "Missed call needs a one-time sign-up: SMS \"REG <account number>\" to 09223488888 from your bank mobile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun BalanceOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

