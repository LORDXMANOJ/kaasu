package com.manoj.kaasu.ui.people

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.ContactRepository
import com.manoj.kaasu.data.repo.ManualEntry
import com.manoj.kaasu.data.repo.PaymentRepository
import com.manoj.kaasu.domain.DebtCalculator
import com.manoj.kaasu.domain.DebtSummary
import com.manoj.kaasu.ui.common.AmountDialog
import com.manoj.kaasu.ui.common.ConfirmDialog
import com.manoj.kaasu.ui.components.Avatar
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.components.EmptyState
import com.manoj.kaasu.ui.components.KCard
import com.manoj.kaasu.ui.components.SectionHeader
import com.manoj.kaasu.ui.components.TxnRow
import com.manoj.kaasu.ui.components.TxnUi
import com.manoj.kaasu.ui.components.toUi
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme
import com.manoj.kaasu.ui.util.rememberLockManager
import com.manoj.kaasu.ui.util.sendWhatsApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class PersonDetailState(
    val loading: Boolean = true,
    val contact: Contact? = null,
    val summary: DebtSummary = DebtSummary(0, 0),
    val ledger: List<TxnUi> = emptyList(),
    val oldestLentAt: Long? = null,
    val nextDueAt: Long? = null,
    val reminderText: String = "",
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val contacts: ContactRepository,
    private val payments: PaymentRepository,
    private val categories: CategoryRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private val _closed = Channel<Unit>(Channel.CONFLATED)
    val closed: Flow<Unit> = _closed.receiveAsFlow()

    val state: StateFlow<PersonDetailState> = combine(
        contacts.observeById(id),
        payments.observeByContact(id),
        categories.categories,
        settings.settings,
    ) { contact, list, cats, s ->
        val catMap = cats.associateBy { it.id }
        val summary = DebtCalculator.summarize(list, catMap)
        val lent = list.filter { DebtCalculator.isLent(it, catMap) }
        val oldest = lent.minOfOrNull { it.createdAt }
        val nextDue = lent.mapNotNull { it.dueAt }.filter { summary.outstandingPaise > 0 }.minOrNull()
        PersonDetailState(
            loading = false,
            contact = contact,
            summary = summary,
            ledger = list.map { it.toUi(catMap, contact?.let { c -> mapOf(c.id to c) } ?: emptyMap()) },
            oldestLentAt = oldest,
            nextDueAt = nextDue,
            reminderText = reminder(contact, summary, oldest, s.myVpa, s.userName),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailState())

    private fun reminder(contact: Contact?, summary: DebtSummary, oldest: Long?, myVpa: String, myName: String): String {
        val name = contact?.name?.substringBefore(' ') ?: "there"
        val since = oldest?.let { " (from ${Dates.dayMonth(Dates.toLocalDate(it))})" }.orEmpty()
        val upi = if (myVpa.isNotBlank()) " to my UPI ID $myVpa" else ""
        val sign = if (myName.isNotBlank()) "\n- $myName" else ""
        return "Hi $name, a small reminder about the ${Money.format(summary.outstandingPaise)} I lent you$since. " +
            "Please send it when you can$upi. Thanks!$sign"
    }

    fun addCash(direction: Direction, amountPaise: Long) = viewModelScope.launch {
        val kind = if (direction == Direction.IN) CategoryKind.REPAYMENT else CategoryKind.LENT
        payments.addManual(
            ManualEntry(
                direction = direction,
                amountPaise = amountPaise,
                categoryId = categories.firstOfKind(kind)?.id,
                contactId = id,
                note = "Cash",
            ),
        )
    }

    fun archive() = viewModelScope.launch {
        contacts.getById(id)?.let { contacts.archive(it) }
        _closed.send(Unit)
    }
}

@Composable
fun PersonDetailScreen(navController: NavHostController, vm: PersonDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lockManager = rememberLockManager()
    var menu by rememberSaveable { mutableStateOf(false) }
    var cashBack by rememberSaveable { mutableStateOf(false) }
    var cashLent by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.closed.collect { navController.popBackStack() } }
    val contact = state.contact

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        BackTopBar(title = contact?.name.orEmpty(), onBack = { navController.popBackStack() }) {
            if (contact != null) {
                IconButton(onClick = { navController.navigate(Routes.personEdit(contact.id)) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit person")
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add cash I lent") }, onClick = { menu = false; cashLent = true })
                    DropdownMenuItem(text = { Text("Remove from People") }, onClick = { menu = false; confirmRemove = true })
                }
            }
        }
        if (contact == null) {
            if (!state.loading) EmptyState(Icons.Rounded.Handshake, "Person not found", "They may have been removed.")
            return@Column
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(contact.name, size = 72.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(contact.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(contact.vpa, contact.phone).joinToString(" · ").ifEmpty { "No UPI ID saved" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item(key = "balance") { BalanceCard(state) }
            item(key = "actions") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val canPay = contact.vpa != null
                    Action(Icons.AutoMirrored.Rounded.Send, "Pay", Modifier.weight(1f)) {
                        navController.navigate(if (canPay) Routes.pay(contactId = contact.id) else Routes.personEdit(contact.id))
                    }
                    Action(Icons.Rounded.Handshake, "Lend", Modifier.weight(1f)) {
                        navController.navigate(if (canPay) Routes.pay(contactId = contact.id, lend = true) else Routes.personEdit(contact.id))
                    }
                    Action(Icons.AutoMirrored.Rounded.CallReceived, "Got cash", Modifier.weight(1f)) { cashBack = true }
                    Action(Icons.Rounded.NotificationsActive, "Remind", Modifier.weight(1f), enabled = state.summary.outstandingPaise > 0) {
                        lockManager.markExternalLaunch()
                        context.sendWhatsApp(contact.phone, state.reminderText)
                    }
                }
            }
            item(key = "ledgerHeader") { SectionHeader("History with ${contact.name.substringBefore(' ')}", Modifier.padding(horizontal = 20.dp)) }
            if (state.ledger.isEmpty()) {
                item(key = "noLedger") {
                    Text(
                        "No payments yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            items(state.ledger, key = { it.id }) { txn ->
                TxnRow(txn, onClick = { navController.navigate(Routes.txn(txn.id)) })
            }
        }
    }

    if (cashBack) {
        AmountDialog(
            title = "Got money back in cash",
            message = "UPI repayments are picked up from your bank SMS automatically.",
            initialPaise = state.summary.outstandingPaise.takeIf { it > 0 },
            confirmLabel = "Add",
            onConfirm = {
                vm.addCash(Direction.IN, it)
                cashBack = false
            },
            onDismiss = { cashBack = false },
        )
    }
    if (cashLent) {
        AmountDialog(
            title = "Cash you lent",
            initialPaise = null,
            confirmLabel = "Add",
            onConfirm = {
                vm.addCash(Direction.OUT, it)
                cashLent = false
            },
            onDismiss = { cashLent = false },
        )
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove ${contact?.name}?",
            message = "Their past payments stay in History.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmRemove = false
                vm.archive()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun BalanceCard(state: PersonDetailState) {
    val s = state.summary
    KCard(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        when {
            s.outstandingPaise > 0 -> {
                Text("Owes you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(s.outstandingPaise), style = AmountStyles.hero, color = KaasuTheme.colors.warning)
            }
            s.outstandingPaise < 0 -> {
                Text("You owe them", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(-s.outstandingPaise), style = AmountStyles.hero)
            }
            else -> {
                Text("All settled", style = MaterialTheme.typography.titleLarge, color = KaasuTheme.colors.success)
            }
        }
        if (s.lentPaise > 0 || s.returnedPaise > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Lent ${Money.format(s.lentPaise)} · Got back ${Money.format(s.returnedPaise)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.nextDueAt?.let { due ->
            val overdue = due < Dates.now()
            Text(
                (if (overdue) "Was due " else "Due ") + Dates.dayMonth(Dates.toLocalDate(due)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (overdue) KaasuTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.heightIn(min = 72.dp),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
