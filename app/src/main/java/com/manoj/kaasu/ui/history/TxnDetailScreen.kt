package com.manoj.kaasu.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentSource
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.upi.UpiUri
import com.manoj.kaasu.ui.common.CategoryPickerSheet
import com.manoj.kaasu.ui.common.ConfirmDialog
import com.manoj.kaasu.ui.components.Avatar
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.components.CategoryIcon
import com.manoj.kaasu.ui.components.EmptyState
import com.manoj.kaasu.ui.components.KCard
import com.manoj.kaasu.ui.components.PrimaryButton
import com.manoj.kaasu.ui.components.StatusPill
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme

@Composable
fun TxnDetailScreen(navController: NavHostController, vm: TxnDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickPerson by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.closed.collect { navController.popBackStack() } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        BackTopBar(title = "Payment details", onBack = { navController.popBackStack() }) {
            if (state.payment != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
            }
        }
        val payment = state.payment
        when {
            state.missing -> EmptyState(Icons.Rounded.SearchOff, "Payment not found", "It may have been deleted.")
            payment == null -> Unit
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Header(state, payment)
                if (payment.status == PaymentStatus.PENDING || payment.status == PaymentStatus.UNCONFIRMED) {
                    PendingCard(payment.status, onPaid = { vm.onEvent(TxnDetailEvent.SetStatus(PaymentStatus.SUCCESS)) }, onFailed = { vm.onEvent(TxnDetailEvent.SetStatus(PaymentStatus.FAILED)) })
                }
                ForCard(state, payment, onChangeCategory = { showPicker = true }, onPerson = { pickPerson = true }, onOpenPerson = { navController.navigate(Routes.person(it)) })
                DetailsCard(state, payment)
                state.sms?.let { sms ->
                    KCard {
                        Text("Bank message", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(sms.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("From ${sms.sender} · ${Dates.full(sms.receivedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (payment.direction == Direction.OUT && payment.payeeVpa != null) {
                    if (payment.status == PaymentStatus.FAILED) {
                        TextButton(onClick = { vm.onEvent(TxnDetailEvent.SetStatus(PaymentStatus.SUCCESS)) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Money did leave my account, mark as paid")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val route = if (payment.contactId != null && state.contact?.vpa != null) {
                                Routes.pay(contactId = payment.contactId, amountPaise = payment.amountPaise)
                            } else {
                                Routes.pay(raw = UpiUri.fromVpa(payment.payeeVpa, payment.payeeName).raw, amountPaise = payment.amountPaise)
                            }
                            navController.navigate(route)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Replay, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pay ${payment.payeeName ?: "again"} again")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    val payment = state.payment
    if (showPicker && payment != null) {
        CategoryPickerSheet(
            title = state.pickerTitle,
            subtitle = "${Money.format(payment.amountPaise)} · ${Dates.full(payment.createdAt)}",
            suggestedTitle = if (payment.direction == Direction.OUT) "Usually at this time" else null,
            suggested = state.pickerOptions.suggested,
            others = state.pickerOptions.others,
            initialCategoryId = payment.categoryId,
            initialNote = payment.note,
            onSave = { id, note ->
                vm.onEvent(TxnDetailEvent.SetCategory(id, note))
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this payment?",
            message = "It's removed from Kaasu only. Your bank and UPI app are not affected.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.onEvent(TxnDetailEvent.Delete)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (pickPerson) {
        AlertDialog(
            onDismissRequest = { pickPerson = false },
            title = { Text("Which person?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (state.contacts.isEmpty()) {
                        Text("No people saved yet. Add them from the People tab.")
                    }
                    state.contacts.forEach { contact ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable {
                                    vm.onEvent(TxnDetailEvent.SetContact(contact.id))
                                    pickPerson = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(contact.name, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(contact.name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (payment?.contactId != null) {
                    TextButton(onClick = {
                        vm.onEvent(TxnDetailEvent.SetContact(null))
                        pickPerson = false
                    }) { Text("Remove person") }
                }
            },
            dismissButton = { TextButton(onClick = { pickPerson = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(state: TxnDetailState, payment: Payment) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        CategoryIcon(
            icon = state.category?.icon ?: if (payment.direction == Direction.IN) "income" else "untagged",
            colorIndex = state.category?.colorIndex ?: 13,
            size = 64.dp,
        )
        Spacer(Modifier.height(12.dp))
        val sign = if (payment.direction == Direction.IN) "+" else "−"
        Text(
            sign + Money.format(payment.amountPaise, forceDecimals = payment.amountPaise % 100 != 0L),
            style = AmountStyles.hero,
            color = if (payment.direction == Direction.IN) KaasuTheme.colors.success else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        StatusPill(payment.status)
        Spacer(Modifier.height(8.dp))
        val who = state.contact?.name ?: payment.payeeName ?: payment.payeeVpa
        if (who != null) {
            Text(
                (if (payment.direction == Direction.IN) "From " else "To ") + who,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        Text(Dates.full(payment.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PendingCard(status: PaymentStatus, onPaid: () -> Unit, onFailed: () -> Unit) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = KaasuTheme.colors.warning)
            Spacer(Modifier.width(10.dp))
            Text(
                if (status == PaymentStatus.PENDING) "Waiting for the bank SMS" else "No bank SMS confirmed this",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Kaasu settles this by itself when the bank message arrives. If you know what happened, you can set it here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onFailed, modifier = Modifier.weight(1f)) { Text("It failed") }
            PrimaryButton(text = "It was paid", onClick = onPaid, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ForCard(
    state: TxnDetailState,
    payment: Payment,
    onChangeCategory: () -> Unit,
    onPerson: () -> Unit,
    onOpenPerson: (Long) -> Unit,
) {
    KCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        ClickRow(
            label = if (payment.direction == Direction.IN) "Type" else "For",
            value = state.category?.name ?: "Untagged, tap to tag",
            valueColor = if (state.category == null) KaasuTheme.colors.warning else MaterialTheme.colorScheme.onSurface,
            onClick = onChangeCategory,
        )
        if (!payment.note.isNullOrBlank()) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ClickRow(label = "Note", value = payment.note, onClick = onChangeCategory)
        }
        val kind = state.category?.kind
        if (kind == CategoryKind.LENT || kind == CategoryKind.REPAYMENT || payment.contactId != null) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val contact = state.contact
            ClickRow(
                label = "Person",
                value = contact?.name ?: "Choose person",
                onClick = { if (contact != null) onOpenPerson(contact.id) else onPerson() },
                onLongClick = onPerson,
            )
            payment.dueAt?.let { due ->
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                InfoRow("Pay back by", Dates.dayMonth(Dates.toLocalDate(due)), copyable = false)
            }
        }
    }
}

@Composable
private fun DetailsCard(state: TxnDetailState, payment: Payment) {
    KCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        val rows = buildList {
            payment.payeeName?.let { add(Triple(if (payment.direction == Direction.IN) "From" else "Paid to", it, false)) }
            payment.payeeVpa?.let { add(Triple("UPI ID", it, true)) }
            payment.bankRef?.let { add(Triple("Bank ref / UTR", it, true)) }
            payment.upiTxnId?.let { add(Triple("UPI transaction ID", it, true)) }
            add(Triple("Kaasu reference", payment.txnRef, true))
            state.upiAppLabel?.let { add(Triple("Paid using", it, false)) }
            add(
                Triple(
                    "Recorded by",
                    when (payment.source) {
                        PaymentSource.APP -> "Kaasu pay screen"
                        PaymentSource.SMS -> "Bank SMS"
                        PaymentSource.MANUAL -> "You (cash entry)"
                    },
                    false,
                ),
            )
            payment.responseCode?.let { add(Triple("UPI response code", it, false)) }
        }
        rows.forEachIndexed { index, (label, value, copy) ->
            InfoRow(label, value, copy)
            if (index < rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, copyable: Boolean) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(132.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (copyable) {
            IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $label", modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClickRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(132.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
