package com.manoj.kaasu.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import android.widget.Toast
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.manoj.kaasu.data.upi.UpiUri
import com.manoj.kaasu.ui.common.TextInputDialog
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.ContactRepository
import com.manoj.kaasu.data.repo.PaymentRepository
import com.manoj.kaasu.domain.DebtCalculator
import com.manoj.kaasu.ui.components.Avatar
import com.manoj.kaasu.ui.components.EmptyState
import com.manoj.kaasu.ui.components.KCard
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class PersonRow(val contact: Contact, val outstandingPaise: Long, val lentPaise: Long)

@Immutable
data class PeopleState(val loading: Boolean = true, val people: List<PersonRow> = emptyList(), val totalOwedPaise: Long = 0)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    contacts: ContactRepository,
    payments: PaymentRepository,
    categories: CategoryRepository,
) : ViewModel() {
    val state: StateFlow<PeopleState> = combine(contacts.active, payments.observeWithContact(), categories.categories) { people, list, cats ->
        val debts = DebtCalculator.byContact(list, cats.associateBy { it.id })
        val rows = people.map { c ->
            val d = debts[c.id]
            PersonRow(c, d?.outstandingPaise ?: 0, d?.lentPaise ?: 0)
        }.sortedWith(compareByDescending<PersonRow> { it.outstandingPaise }.thenBy { it.contact.name.lowercase() })
        PeopleState(false, rows, rows.sumOf { it.outstandingPaise.coerceAtLeast(0) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleState())
}

@Composable
fun PeopleScreen(navController: NavHostController, vm: PeopleViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var payNumber by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp + com.manoj.kaasu.ui.navigation.LocalBarInset.current),
        ) {
            item(key = "title") {
                Text(
                    "People",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            item(key = "summary") {
                KCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Friends owe you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Money.format(state.totalOwedPaise),
                        style = AmountStyles.hero,
                        color = if (state.totalOwedPaise > 0) KaasuTheme.colors.warning else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Lend from a person's page. When they pay you back by UPI, Kaasu spots it in your bank SMS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!state.loading && state.people.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Group,
                        title = "No people yet",
                        message = "Save friends with their UPI ID to pay them in two taps and keep track of money you lend.",
                        actionLabel = "Add a person",
                        onAction = { navController.navigate(Routes.personEdit()) },
                    )
                }
            }
            items(state.people, key = { it.contact.id }) { row ->
                PersonListRow(row) { navController.navigate(Routes.person(row.contact.id)) }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + com.manoj.kaasu.ui.navigation.LocalBarInset.current),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.PICK_PERSON) },
                icon = { Icon(Icons.Rounded.Dialpad, contentDescription = null) },
                text = { Text("Pay") },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.personEdit()) },
                icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
    if (payNumber) {
        TextInputDialog(
            title = "Pay a mobile number",
            initial = "",
            label = "Mobile number",
            placeholder = "98765 43210",
            keyboardType = KeyboardType.Phone,
            allowEmpty = false,
            onConfirm = { number ->
                val vpa = UpiUri.upiNumberVpa(number)
                if (vpa == null) {
                    Toast.makeText(context, "Type a 10-digit Indian mobile number", Toast.LENGTH_SHORT).show()
                } else {
                    payNumber = false
                    navController.navigate(Routes.pay(raw = vpa))
                }
            },
            onDismiss = { payNumber = false },
        )
    }
}

@Composable
private fun PersonListRow(row: PersonRow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(row.contact.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.contact.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                row.contact.vpa ?: "No UPI ID yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            when {
                row.outstandingPaise > 0 -> {
                    Text("owes you", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(row.outstandingPaise), style = AmountStyles.row, color = KaasuTheme.colors.warning)
                }
                row.outstandingPaise < 0 -> {
                    Text("you owe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(-row.outstandingPaise), style = AmountStyles.row)
                }
                row.lentPaise > 0 -> Text("Settled", style = MaterialTheme.typography.labelLarge, color = KaasuTheme.colors.success)
            }
        }
    }
}
