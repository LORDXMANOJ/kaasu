package com.manoj.kaasu.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.manoj.kaasu.BuildConfig
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.core.SlotBoundaries
import com.manoj.kaasu.data.notif.NotifStatus
import com.manoj.kaasu.data.prefs.ThemeMode
import com.manoj.kaasu.data.upi.UpiUri
import com.manoj.kaasu.security.BiometricHelper
import com.manoj.kaasu.ui.common.AmountDialog
import com.manoj.kaasu.ui.common.ConfirmDialog
import com.manoj.kaasu.ui.common.PinSetup
import com.manoj.kaasu.ui.common.TextInputDialog
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.util.hasNotificationAccess
import com.manoj.kaasu.ui.util.openNotificationAccessSettings
import com.manoj.kaasu.ui.util.rememberLockManager

private enum class Edit { NAME, VPA, RECIPIENT, BUDGET, BALANCE, SLOTS, UPI_APP, PIN, RESTORE_CONFIRM }

@Composable
fun SettingsScreen(navController: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state.settings
    val context = LocalContext.current
    val lockManager = rememberLockManager()
    var editing by rememberSaveable { mutableStateOf<Edit?>(null) }
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    var pickStyle by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    var notifAccess by remember { mutableStateOf(context.hasNotificationAccess()) }
    var notifStatus by remember { mutableStateOf(NotifStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshPermission()
        notifAccess = context.hasNotificationAccess()
        notifStatus = NotifStatus.read(context)
    }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.READ_SMS] == true
        if (granted) vm.setSmsEnabled(true) else Toast.makeText(context, "SMS permission was not given", Toast.LENGTH_SHORT).show()
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::backupTo)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestore = uri.toString()
            editing = Edit.RESTORE_CONFIRM
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        BackTopBar(title = "Settings", onBack = { navController.popBackStack() })
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Section("You") {
                SettingRow(Icons.Rounded.Person, "Your name", s.userName.ifBlank { "Not set" }) { editing = Edit.NAME }
                SettingRow(Icons.Rounded.AlternateEmail, "Your UPI ID", s.myVpa.ifBlank { "Added to payback reminders" }) { editing = Edit.VPA }
                SettingRow(Icons.AutoMirrored.Rounded.Send, "Weekly report goes to", s.reportRecipient) { editing = Edit.RECIPIENT }
            }
            Section("Budget") {
                SettingRow(Icons.Rounded.Savings, "Daily budget", Money.format(s.dailyBudgetPaise)) { editing = Edit.BUDGET }
                SwitchRow(
                    Icons.Rounded.Handshake,
                    "Count money I lend in the budget",
                    "Off: lending is tracked separately in People",
                    s.countLentInBudget,
                    vm::setCountLent,
                )
            }
            Section("Time of day") {
                SettingRow(
                    Icons.Rounded.Schedule,
                    "Morning · Afternoon · Evening · Night",
                    "${hour(s.morningStart)} · ${hour(s.afternoonStart)} · ${hour(s.eveningStart)} · ${hour(s.nightStart)}",
                ) { editing = Edit.SLOTS }
                SettingRow(Icons.Rounded.Category, "Categories", "Edit names, icons, and what shows on top when") {
                    navController.navigate(Routes.CATEGORIES)
                }
            }
            Section("Payments") {
                SettingRow(Icons.Rounded.Wallet, "UPI app", state.defaultAppLabel ?: "Ask every time") { editing = Edit.UPI_APP }
            }
            Section("Received money") {
                SwitchRow(
                    Icons.Rounded.NotificationsActive,
                    "Read UPI app alerts",
                    if (notifAccess) {
                        val st = notifStatus
                        "On · only Paytm, PhonePe, super.money, GPay, BHIM and SBI alerts are read. Nothing leaves this phone." +
                            "\n${st.recorded} recorded · ${st.seen} read" +
                            (if (st.connectedAt > 0) " · connected ${Dates.relative(st.connectedAt)}" else " · waiting for Android to connect") +
                            st.recent.joinToString("") { "\n• $it" }
                    } else {
                        "Catches money friends send you, even when the bank sends no SMS"
                    },
                    notifAccess,
                ) {
                    lockManager.markExternalLaunch()
                    context.openNotificationAccessSettings()
                }
                if (!notifAccess) {
                    Text(
                        "If Android greys it out: App info → ⋮ → Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            }
            Section("Bank SMS") {
                SwitchRow(
                    Icons.Rounded.Sms,
                    "Read bank SMS",
                    if (s.smsSyncEnabled && state.smsPermission) {
                        "${state.smsEventCount} bank messages used" + if (s.lastSmsScanAt > 0) " · checked ${Dates.relative(s.lastSmsScanAt)}" else ""
                    } else {
                        "Confirms payments and finds ones made in other apps"
                    },
                    s.smsSyncEnabled && state.smsPermission,
                ) { on ->
                    when {
                        !on -> vm.setSmsEnabled(false)
                        state.smsPermission -> vm.setSmsEnabled(true)
                        else -> smsPermission.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    }
                }
                if (s.smsSyncEnabled && state.smsPermission) {
                    SettingRow(Icons.Rounded.Sync, "Check last 30 days again", "Finds any bank transactions that were missed") { vm.rescan() }
                }
                SettingRow(
                    Icons.Rounded.AccountBalanceWallet,
                    "Bank balance",
                    s.openingBalancePaise?.let { "Set to ${Money.format(it)} on ${Dates.dayMonth(Dates.toLocalDate(s.openingBalanceAt ?: 0))}" }
                        ?: "Type what your bank shows, if SMS don't include it",
                ) { editing = Edit.BALANCE }
            }
            Section("Security") {
                SwitchRow(
                    Icons.Rounded.Lock,
                    "Lock with PIN",
                    "Never asks right after you pay",
                    s.isLockConfigured,
                ) { on -> if (on) editing = Edit.PIN else vm.disableLock() }
                if (s.isLockConfigured) {
                    SettingRow(Icons.Rounded.Password, "Change PIN", null) { editing = Edit.PIN }
                    if (BiometricHelper.isAvailable(context)) {
                        SwitchRow(Icons.Rounded.Fingerprint, "Unlock with fingerprint", null, s.biometricEnabled, vm::setBiometric)
                    }
                }
            }
            Section("Look") {
                SettingRow(Icons.Rounded.Palette, "Style", com.manoj.kaasu.ui.home.styles.HomeStyle.from(s.homeStyle).label) { pickStyle = true }
            }
            Section("Your data") {
                SettingRow(Icons.Rounded.Backup, "Back up to a file", "Save everything to Drive or the phone") {
                    lockManager.markExternalLaunch()
                    backupLauncher.launch("kaasu-backup-${Dates.today()}.json")
                }
                SettingRow(Icons.Rounded.Restore, "Restore from a backup", "Replaces what's in Kaasu now") {
                    lockManager.markExternalLaunch()
                    restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }
                SettingRow(Icons.Rounded.TableChart, "Export for Excel (CSV)", "All payments with IDs and UTRs") {
                    lockManager.markExternalLaunch()
                    csvLauncher.launch("kaasu-payments-${Dates.today()}.csv")
                }
            }
            Text(
                "Kaasu ${BuildConfig.VERSION_NAME} · No ads, no tracking. Everything stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }

    if (pickStyle) {
        com.manoj.kaasu.ui.home.styles.StylePickerSheet(
            current = com.manoj.kaasu.ui.home.styles.HomeStyle.from(s.homeStyle),
            onPick = { vm.setHomeStyle(it.name) },
            onDismiss = { pickStyle = false },
        )
    }

    when (editing) {
        Edit.NAME -> TextInputDialog("Your name", s.userName, { vm.setName(it); editing = null }, { editing = null }, allowEmpty = false)
        Edit.VPA -> TextInputDialog(
            title = "Your UPI ID",
            initial = s.myVpa,
            onConfirm = { v ->
                if (v.isBlank() || UpiUri.isValidVpa(v)) {
                    vm.setMyVpa(v)
                    editing = null
                } else {
                    Toast.makeText(context, "That doesn't look like a UPI ID", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { editing = null },
            placeholder = "yourname@okaxis",
            keyboardType = KeyboardType.Email,
        )
        Edit.RECIPIENT -> TextInputDialog("Report goes to", s.reportRecipient, { vm.setRecipient(it); editing = null }, { editing = null }, placeholder = "Dad")
        Edit.BUDGET -> AmountDialog("Daily budget", s.dailyBudgetPaise, { vm.setDailyBudget(it); editing = null }, { editing = null })
        Edit.BALANCE -> AmountDialog(
            title = "Bank balance",
            message = "Type what your bank app shows right now.",
            initialPaise = s.openingBalancePaise,
            onConfirm = { vm.setBalance(it); editing = null },
            onDismiss = { editing = null },
            onClear = if (s.openingBalancePaise != null) {
                { vm.setBalance(null); editing = null }
            } else {
                null
            },
        )
        Edit.SLOTS -> SlotsDialog(s.slots, onSave = { vm.setSlots(it); editing = null }, onDismiss = { editing = null })
        Edit.UPI_APP -> AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("UPI app to open") },
            text = {
                Column {
                    OptionRow("Ask every time", s.defaultUpiPackage == null) { vm.setDefaultUpi(null); editing = null }
                    state.upiApps.forEach { app ->
                        OptionRow(app.label, s.defaultUpiPackage == app.packageName) { vm.setDefaultUpi(app.packageName); editing = null }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
        )
        Edit.PIN -> Dialog(onDismissRequest = { editing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                    BackTopBar(title = "Set PIN", onBack = { editing = null })
                    PinSetup(
                        onComplete = {
                            vm.setPin(it)
                            editing = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
        Edit.RESTORE_CONFIRM -> ConfirmDialog(
            title = "Restore this backup?",
            message = "Everything in Kaasu now will be replaced by the backup.",
            confirmLabel = "Restore",
            destructive = true,
            onConfirm = {
                pendingRestore?.let { vm.restoreFrom(android.net.Uri.parse(it)) }
                pendingRestore = null
                editing = null
            },
            onDismiss = {
                pendingRestore = null
                editing = null
            },
        )
        null -> Unit
    }
}

private fun hour(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 20.dp, top = 20.dp, bottom = 6.dp)
            .semantics { heading() },
    )
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SlotsDialog(initial: SlotBoundaries, onSave: (SlotBoundaries) -> Unit, onDismiss: () -> Unit) {
    var morning by rememberSaveable { mutableIntStateOf(initial.morningStart) }
    var afternoon by rememberSaveable { mutableIntStateOf(initial.afternoonStart) }
    var evening by rememberSaveable { mutableIntStateOf(initial.eveningStart) }
    var night by rememberSaveable { mutableIntStateOf(initial.nightStart) }
    val slots = SlotBoundaries(morning, afternoon, evening, night)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("When does each part of the day start?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HourStepper("Morning", morning) { morning = it }
                HourStepper("Afternoon", afternoon) { afternoon = it }
                HourStepper("Evening", evening) { evening = it }
                HourStepper("Night", night) { night = it }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    if (slots.isValid()) "The chips you see first change at these times." else "Each one has to start after the one before.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (slots.isValid()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(slots) }, enabled = slots.isValid()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HourStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value + 23) % 24) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(hour(value), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(64.dp))
        IconButton(onClick = { onChange((value + 1) % 24) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

