package com.manoj.kaasu.ui.pay

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.upi.UpiApp
import com.manoj.kaasu.data.upi.UpiAppResolver
import com.manoj.kaasu.domain.BudgetCheck
import com.manoj.kaasu.ui.common.CategoryChipGrid
import com.manoj.kaasu.ui.components.Avatar
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.components.EmptyState
import com.manoj.kaasu.ui.components.PrimaryButton
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun PayScreen(navController: NavHostController, vm: PayViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onEvent(PayEvent.UpiReturned(UpiAppResolver.responseText(result.data)))
    }
    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is PayEffect.LaunchUpi -> try {
                    launcher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(effect.link)).setPackage(effect.packageName))
                } catch (e: ActivityNotFoundException) {
                    vm.onEvent(PayEvent.LaunchFailed)
                }
                PayEffect.Finished -> {
                    navController.popBackStack()
                    if (navController.currentDestination?.route?.startsWith("scan") == true) navController.popBackStack()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        BackTopBar(title = if (state.isLent) "Lend money" else "Pay", onBack = { navController.popBackStack() })
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> PayError(
                message = state.error!!,
                contactId = state.errorContactId,
                onScanAgain = { navController.popBackStack(); navController.navigate(Routes.scan()) },
                onEditPerson = { id -> navController.popBackStack(); navController.navigate(Routes.personEdit(id)) },
            )
            else -> PayContent(state, vm::onEvent, Modifier.weight(1f))
        }
    }

    if (state.showAppPicker) {
        UpiAppPicker(
            apps = state.apps,
            onChoose = { pkg, remember -> vm.onEvent(PayEvent.AppChosen(pkg, remember)) },
            onDismiss = { vm.onEvent(PayEvent.DismissAppPicker) },
        )
    }
}

@Composable
private fun PayContent(state: PayState, onEvent: (PayEvent) -> Unit, modifier: Modifier) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state.amountText.isEmpty() && !state.amountLocked) {
            amountFocus.requestFocus()
            keyboard?.show()
        }
    }
    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            PayeeHeader(state)
            Spacer(Modifier.height(20.dp))
            AmountInput(
                text = state.amountText,
                locked = state.amountLocked,
                focusRequester = amountFocus,
                onChange = { onEvent(PayEvent.AmountChanged(it)) },
                onDone = { focusManager.clearFocus() },
            )
            Spacer(Modifier.height(10.dp))
            BudgetLine(state.budget, state.amountPaise)
            Spacer(Modifier.height(24.dp))
            Text("What's it for?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            CategoryChipGrid(
                suggestedTitle = "${state.slot.label} picks",
                suggested = state.suggested,
                others = state.others,
                selectedId = state.selectedCategoryId,
                onSelect = {
                    focusManager.clearFocus()
                    onEvent(PayEvent.CategorySelected(it.id))
                },
            )
            AnimatedVisibility(visible = state.isLent, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                LendDetails(state, onEvent)
            }
            Spacer(Modifier.height(16.dp))
            NoteField(state, onEvent)
            Spacer(Modifier.height(24.dp))
        }
        PayBar(state, onEvent)
    }
}

@Composable
private fun PayeeHeader(state: PayState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.isMerchant && state.contactId == null) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Avatar(state.payeeName, size = 52.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(state.payeeName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                state.payeeVpa,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.contactId != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = KaasuTheme.colors.success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Saved", style = MaterialTheme.typography.labelMedium, color = KaasuTheme.colors.success)
            }
        }
    }
}

@Composable
private fun AmountInput(
    text: String,
    locked: Boolean,
    focusRequester: FocusRequester,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                "₹",
                style = AmountStyles.input,
                color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(4.dp))
            BasicTextField(
                value = text,
                onValueChange = onChange,
                readOnly = locked,
                singleLine = true,
                textStyle = AmountStyles.input.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 40.dp)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "Amount in rupees" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text("0", style = AmountStyles.input, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                        inner()
                    }
                },
            )
        }
        if (locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("Amount is fixed by this QR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BudgetLine(budget: BudgetCheck?, amountPaise: Long?) {
    if (budget == null) return
    val colors = KaasuTheme.colors
    val (icon, text, color) = when {
        !budget.countsTowardBudget -> Triple(Icons.Rounded.Info, "Lending isn't counted in your daily budget", MaterialTheme.colorScheme.onSurfaceVariant)
        budget.overByPaise > 0 -> Triple(
            Icons.Rounded.WarningAmber,
            "This takes you ${Money.format(budget.overByPaise)} over today's ${Money.format(budget.budgetPaise)} budget. You can still pay.",
            colors.warning,
        )
        budget.categoryOverByPaise > 0 -> Triple(
            Icons.Rounded.WarningAmber,
            "${Money.format(budget.categoryOverByPaise)} over your daily limit for this category. You can still pay.",
            colors.warning,
        )
        amountPaise == null -> Triple(
            Icons.Rounded.Info,
            "${Money.format((budget.budgetPaise - budget.spentPaise).coerceAtLeast(0))} left in today's budget",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Triple(
            Icons.Rounded.CheckCircle,
            "${Money.format(budget.budgetPaise - budget.afterPaise)} left today after this",
            colors.success,
        )
    }
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LendDetails(state: PayState, onEvent: (PayEvent) -> Unit) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.contactId == null) {
            OutlinedTextField(
                value = state.lentName,
                onValueChange = { onEvent(PayEvent.LentNameChanged(it)) },
                label = { Text("Lending to") },
                supportingText = { Text("Saved to People so you can track when they pay back") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("When should they pay back?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val today = Dates.today()
        val options = listOf("No date" to null, "Tomorrow" to today.plusDays(1), "In 3 days" to today.plusDays(3), "In a week" to today.plusWeeks(1))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, date) ->
                val millis = date?.let { Dates.endOfDay(it) }
                FilterChip(
                    selected = state.dueAt == millis,
                    onClick = { onEvent(PayEvent.DueChanged(millis)) },
                    label = { Text(label) },
                    modifier = Modifier.height(40.dp),
                )
            }
            val custom = state.dueAt != null && options.none { (_, d) -> d != null && Dates.endOfDay(d) == state.dueAt }
            FilterChip(
                selected = custom,
                onClick = { pickDate = true },
                label = { Text(if (custom) Dates.dayMonth(Dates.toLocalDate(state.dueAt!!)) else "Pick date") },
                modifier = Modifier.height(40.dp),
            )
        }
    }
    if (pickDate) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        onEvent(PayEvent.DueChanged(Dates.endOfDay(date)))
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun NoteField(state: PayState, onEvent: (PayEvent) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val show = expanded || state.needsNote || state.note.isNotEmpty()
    if (show) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(state.needsNote) { if (state.needsNote && state.note.isEmpty()) focus.requestFocus() }
        OutlinedTextField(
            value = state.note,
            onValueChange = { onEvent(PayEvent.NoteChanged(it)) },
            label = { Text(if (state.needsNote) "What is it for?" else "Note (optional)") },
            placeholder = { Text(if (state.needsNote) "e.g. Birthday gift for Anu" else "e.g. Idli and vada") },
            isError = state.needsNote && state.validation != null && state.note.isBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
    } else {
        TextButton(onClick = { expanded = true }) { Text("+ Add a note") }
    }
}

@Composable
private fun PayBar(state: PayState, onEvent: (PayEvent) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            AnimatedVisibility(visible = state.validation != null) {
                Text(
                    state.validation.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            val amount = state.amountPaise
            PrimaryButton(
                text = if (amount != null) "${if (state.isLent) "Lend" else "Pay"} ${Money.format(amount)}" else "Pay",
                loading = state.launching,
                onClick = { onEvent(PayEvent.PayClicked) },
            )
            if (state.apps.size > 1 || state.defaultApp != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.defaultApp?.let { "Opens ${it.label}" } ?: "You'll choose the UPI app next",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.defaultApp != null) {
                        TextButton(onClick = { onEvent(PayEvent.ChangeApp) }) { Text("Change") }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpiAppPicker(apps: List<UpiApp>, onChoose: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var remember by rememberSaveable { mutableStateOf(true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text("Pay with", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "You'll only enter your UPI PIN there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            apps.forEachIndexed { index, app ->
                val icon: ImageBitmap? = remember(app.packageName) { app.icon?.toBitmap(96, 96)?.asImageBitmap() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { onChoose(app.packageName, remember) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    } else {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                }
                if (index < apps.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { remember = !remember }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = remember, onCheckedChange = { remember = it })
                Text("Always use this app (change any time)", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PayError(message: String, contactId: Long?, onScanAgain: () -> Unit, onEditPerson: (Long) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        EmptyState(
            icon = Icons.Rounded.QrCodeScanner,
            title = "Can't pay this one",
            message = message,
            actionLabel = if (contactId != null) "Add their UPI ID" else "Scan again",
            onAction = { if (contactId != null) onEditPerson(contactId) else onScanAgain() },
        )
    }
}
