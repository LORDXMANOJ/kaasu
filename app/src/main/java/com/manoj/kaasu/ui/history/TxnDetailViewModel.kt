package com.manoj.kaasu.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.ContactRepository
import com.manoj.kaasu.data.repo.PaymentRepository
import com.manoj.kaasu.data.repo.SmsRepository
import com.manoj.kaasu.data.upi.UpiAppResolver
import com.manoj.kaasu.domain.CategoryOrdering
import com.manoj.kaasu.domain.OrderedCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class TxnDetailState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val payment: Payment? = null,
    val category: Category? = null,
    val contact: Contact? = null,
    val upiAppLabel: String? = null,
    val sms: SmsEvent? = null,
    val pickerOptions: OrderedCategories = OrderedCategories(emptyList(), emptyList()),
    val pickerTitle: String = "",
    val contacts: List<Contact> = emptyList(),
)

sealed interface TxnDetailEvent {
    data class SetCategory(val categoryId: Long, val note: String?) : TxnDetailEvent
    data class SetStatus(val status: PaymentStatus) : TxnDetailEvent
    data class SetContact(val contactId: Long?) : TxnDetailEvent
    data object Delete : TxnDetailEvent
}

@HiltViewModel
class TxnDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val payments: PaymentRepository,
    categories: CategoryRepository,
    contacts: ContactRepository,
    settings: SettingsRepository,
    private val sms: SmsRepository,
    private val upiApps: UpiAppResolver,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private val _closed = Channel<Unit>(Channel.CONFLATED)
    val closed: Flow<Unit> = _closed.receiveAsFlow()

    val state: StateFlow<TxnDetailState> = combine(
        payments.observeById(id),
        categories.categories,
        contacts.active,
        settings.settings.map { it.slots },
    ) { payment, cats, people, slots ->
        if (payment == null) return@combine TxnDetailState(loading = false, missing = true)
        val ordered = if (payment.direction == Direction.IN) {
            OrderedCategories(CategoryOrdering.forIncoming(cats), emptyList())
        } else {
            CategoryOrdering.forPayment(cats, slots.slotFor(payment.createdAt), toPerson = payment.contactId != null)
        }
        TxnDetailState(
            loading = false,
            payment = payment,
            category = cats.firstOrNull { it.id == payment.categoryId },
            contact = people.firstOrNull { it.id == payment.contactId },
            upiAppLabel = upiApps.label(payment.upiApp) ?: payment.upiApp,
            sms = payment.smsEventId?.let { sms.getById(it) },
            pickerOptions = ordered,
            pickerTitle = if (payment.direction == Direction.IN) "What was this money?" else "What was this for?",
            contacts = people,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnDetailState())

    fun onEvent(event: TxnDetailEvent) {
        viewModelScope.launch {
            when (event) {
                is TxnDetailEvent.SetCategory -> payments.setCategory(id, event.categoryId, event.note)
                is TxnDetailEvent.SetStatus -> payments.setStatus(id, event.status)
                is TxnDetailEvent.SetContact -> payments.getById(id)?.let { payments.update(it.copy(contactId = event.contactId)) }
                TxnDetailEvent.Delete -> {
                    payments.getById(id)?.let { payments.delete(it) }
                    _closed.send(Unit)
                }
            }
        }
    }
}
