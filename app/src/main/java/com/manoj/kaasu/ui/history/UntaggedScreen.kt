package com.manoj.kaasu.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.ContactRepository
import com.manoj.kaasu.data.repo.PaymentRepository
import com.manoj.kaasu.domain.CategoryOrdering
import com.manoj.kaasu.domain.OrderedCategories
import com.manoj.kaasu.ui.common.CategoryPickerSheet
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.components.EmptyState
import com.manoj.kaasu.ui.components.TxnRow
import com.manoj.kaasu.ui.components.TxnUi
import com.manoj.kaasu.ui.components.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class UntaggedItem(val payment: Payment, val ui: TxnUi, val options: OrderedCategories)

@Immutable
data class UntaggedState(val loading: Boolean = true, val items: List<UntaggedItem> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UntaggedViewModel @Inject constructor(
    private val payments: PaymentRepository,
    categories: CategoryRepository,
    contacts: ContactRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<UntaggedState> = combine(
        settings.settings.flatMapLatest { payments.observeUntagged(it.untaggedSince) },
        categories.categories,
        contacts.all,
        settings.settings,
    ) { list, cats, people, s ->
        val catMap = cats.associateBy { it.id }
        val peopleMap = people.associateBy { it.id }
        UntaggedState(
            loading = false,
            items = list.map { p ->
                UntaggedItem(
                    payment = p,
                    ui = p.toUi(catMap, peopleMap),
                    options = CategoryOrdering.forPayment(cats, s.slots.slotFor(p.createdAt), toPerson = p.contactId != null),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UntaggedState())

    fun tag(paymentId: Long, categoryId: Long, note: String?) = viewModelScope.launch {
        payments.setCategory(paymentId, categoryId, note)
    }

    fun delete(payment: Payment) = viewModelScope.launch { payments.delete(payment) }
}

@Composable
fun UntaggedScreen(navController: androidx.navigation.NavHostController, vm: UntaggedViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        BackTopBar(title = "Needs a tag", onBack = { navController.popBackStack() })
        if (!state.loading && state.items.isEmpty()) {
            EmptyState(Icons.Rounded.TaskAlt, "All tagged", "Every payment has a reason. Nice!")
        } else {
            Text(
                "These were paid outside Kaasu and found in your bank SMS. Tap one to say what it was for.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.items, key = { it.payment.id }) { item ->
                    TxnRow(item.ui, onClick = { selectedId = item.payment.id })
                }
            }
        }
    }
    val selected = state.items.firstOrNull { it.payment.id == selectedId }
    if (selected != null) {
        CategoryPickerSheet(
            title = "What was this for?",
            subtitle = "${Money.format(selected.payment.amountPaise)} to ${selected.ui.title} · ${Dates.full(selected.payment.createdAt)}",
            suggestedTitle = "Usually at this time",
            suggested = selected.options.suggested,
            others = selected.options.others,
            initialCategoryId = null,
            initialNote = selected.payment.note,
            onSave = { categoryId, note ->
                vm.tag(selected.payment.id, categoryId, note)
                selectedId = null
            },
            onDismiss = { selectedId = null },
            onDelete = {
                vm.delete(selected.payment)
                selectedId = null
            },
        )
    }
}
