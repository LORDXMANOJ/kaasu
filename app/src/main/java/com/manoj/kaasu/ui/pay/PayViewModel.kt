package com.manoj.kaasu.ui.pay

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.core.TimeSlot
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.prefs.UserSettings
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.ContactRepository
import com.manoj.kaasu.data.repo.NewAppPayment
import com.manoj.kaasu.data.repo.PaymentRepository
import com.manoj.kaasu.data.sms.SyncScheduler
import com.manoj.kaasu.data.upi.UpiApp
import com.manoj.kaasu.data.upi.UpiAppResolver
import com.manoj.kaasu.data.upi.UpiPayload
import com.manoj.kaasu.data.upi.UpiResponseParser
import com.manoj.kaasu.data.upi.UpiUri
import com.manoj.kaasu.di.AppScope
import com.manoj.kaasu.domain.BudgetCheck
import com.manoj.kaasu.domain.BudgetRules
import com.manoj.kaasu.domain.CategoryOrdering
import com.manoj.kaasu.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import javax.inject.Inject

@Immutable
data class PayState(
    val loading: Boolean = true,
    val error: String? = null,
    val errorContactId: Long? = null,
    val payeeName: String = "",
    val payeeVpa: String = "",
    val isMerchant: Boolean = false,
    val contactId: Long? = null,
    val amountText: String = "",
    val amountLocked: Boolean = false,
    val slot: TimeSlot = TimeSlot.MORNING,
    val suggested: List<Category> = emptyList(),
    val others: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val note: String = "",
    val lentName: String = "",
    val dueAt: Long? = null,
    val budget: BudgetCheck? = null,
    val apps: List<UpiApp> = emptyList(),
    val defaultApp: UpiApp? = null,
    val showAppPicker: Boolean = false,
    val launching: Boolean = false,
    val validation: String? = null,
) {
    val amountPaise: Long? get() = Money.parseToPaise(amountText)?.takeIf { it > 0 }
    val selectedCategory: Category? get() = (suggested + others).firstOrNull { it.id == selectedCategoryId }
    val isLent: Boolean get() = selectedCategory?.kind == CategoryKind.LENT
    val needsNote: Boolean get() = selectedCategory?.kind == CategoryKind.OTHER
}

sealed interface PayEvent {
    data class AmountChanged(val text: String) : PayEvent
    data class CategorySelected(val id: Long) : PayEvent
    data class NoteChanged(val text: String) : PayEvent
    data class LentNameChanged(val text: String) : PayEvent
    data class DueChanged(val millis: Long?) : PayEvent
    data object PayClicked : PayEvent
    data object ChangeApp : PayEvent
    data class AppChosen(val packageName: String, val remember: Boolean) : PayEvent
    data object DismissAppPicker : PayEvent
    data class UpiReturned(val response: String?) : PayEvent
    data object LaunchFailed : PayEvent
}

sealed interface PayEffect {
    data class LaunchUpi(val link: String, val packageName: String) : PayEffect
    data object Finished : PayEffect
}

@HiltViewModel
class PayViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val settingsRepo: SettingsRepository,
    private val payments: PaymentRepository,
    private val categories: CategoryRepository,
    private val contacts: ContactRepository,
    private val upiApps: UpiAppResolver,
    private val syncScheduler: SyncScheduler,
    private val lockManager: AppLockManager,
    private val appScope: AppScope,
) : ViewModel() {

    private val raw: String = savedState.get<String>("raw").orEmpty()
    private val argContactId: Long = savedState.get<Long>("contactId") ?: 0L
    private val argLend: Boolean = savedState.get<Boolean>("lend") ?: false
    private val argAmount: Long = savedState.get<Long>("amount") ?: 0L

    private val _state = MutableStateFlow(PayState())
    val state: StateFlow<PayState> = _state.asStateFlow()

    private val _effects = Channel<PayEffect>(Channel.BUFFERED)
    val effects: Flow<PayEffect> = _effects.receiveAsFlow()

    private var payload: UpiPayload? = null
    private var payingSavedPerson = false
    private var settings = UserSettings()
    private var todayPayments: List<Payment> = emptyList()
    private var categoryMap: Map<Long, Category> = emptyMap()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        categories.ensureSeeded()
        settings = settingsRepo.current()
        val allCategories = categories.getAll()
        categoryMap = allCategories.associateBy { it.id }
        todayPayments = payments.getRange(Dates.today(), Dates.today())
        val apps = withContext(Dispatchers.IO) { upiApps.installedApps() }
        var contact = if (argContactId > 0) contacts.getById(argContactId) else null
        payingSavedPerson = contact != null
        val parsed = when {
            contact != null -> contact.vpa?.let { UpiUri.fromVpa(it, contact.name) }
            else -> UpiUri.parse(raw)
        }
        if (parsed == null) {
            _state.update {
                it.copy(
                    loading = false,
                    error = if (contact != null) "${contact.name} has no UPI ID saved yet." else "This QR code isn't a UPI payment code.",
                    errorContactId = contact?.id,
                )
            }
            return
        }
        if (contact == null) contact = contacts.findByVpa(parsed.vpa)
        payload = parsed
        val slot = settings.slots.slotFor(LocalTime.now().hour)
        val ordered = CategoryOrdering.forPayment(allCategories, slot, toPerson = contact != null)
        val lendId = if (argLend) allCategories.firstOrNull { it.kind == CategoryKind.LENT && !it.archived }?.id else null
        val amount = when {
            argAmount > 0 -> argAmount
            else -> parsed.amountPaise
        }
        _state.update {
            it.copy(
                loading = false,
                payeeName = contact?.name ?: parsed.name ?: parsed.vpa.substringBefore('@'),
                payeeVpa = parsed.vpa,
                isMerchant = parsed.isMerchant,
                contactId = contact?.id,
                amountText = amount?.let { a -> Money.toInputText(a) }.orEmpty(),
                amountLocked = parsed.isAmountLocked,
                slot = slot,
                suggested = ordered.suggested,
                others = ordered.others,
                selectedCategoryId = lendId,
                lentName = contact?.name ?: parsed.name.orEmpty(),
                apps = apps,
                defaultApp = apps.firstOrNull { app -> app.packageName == settings.defaultUpiPackage },
            )
        }
        refreshBudget()
    }

    fun onEvent(event: PayEvent) {
        when (event) {
            is PayEvent.AmountChanged -> {
                if (_state.value.amountLocked) return
                val cleaned = event.text.replace(",", "").trim()
                if (cleaned.isEmpty() || AMOUNT_PATTERN.matches(cleaned)) {
                    _state.update { it.copy(amountText = cleaned, validation = null) }
                    refreshBudget()
                }
            }
            is PayEvent.CategorySelected -> {
                _state.update { it.copy(selectedCategoryId = event.id, validation = null) }
                refreshBudget()
            }
            is PayEvent.NoteChanged -> _state.update { it.copy(note = event.text.take(60), validation = null) }
            is PayEvent.LentNameChanged -> _state.update { it.copy(lentName = event.text.take(40), validation = null) }
            is PayEvent.DueChanged -> _state.update { it.copy(dueAt = event.millis) }
            PayEvent.PayClicked -> onPay()
            PayEvent.ChangeApp -> _state.update { it.copy(showAppPicker = true) }
            is PayEvent.AppChosen -> {
                _state.update { it.copy(showAppPicker = false) }
                startPayment(event.packageName, event.remember)
            }
            PayEvent.DismissAppPicker -> _state.update { it.copy(showAppPicker = false) }
            is PayEvent.UpiReturned -> onReturned(event.response)
            PayEvent.LaunchFailed -> onLaunchFailed()
        }
    }

    private fun refreshBudget() {
        val s = _state.value
        val amount = s.amountPaise ?: 0L
        _state.update {
            it.copy(budget = BudgetRules.check(amount, s.selectedCategory, todayPayments, categoryMap, settings))
        }
    }

    private fun validationError(): String? {
        val s = _state.value
        val amount = s.amountPaise
        val category = s.selectedCategory
        return when {
            amount == null -> "Enter how much to pay"
            amount > UPI_LIMIT_PAISE -> "UPI allows up to ₹1,00,000 in one payment"
            category == null -> "Pick what this payment is for"
            s.needsNote && s.note.isBlank() -> "Type what it's for"
            s.isLent && s.contactId == null && s.lentName.isBlank() -> "Type who you're lending to"
            else -> null
        }
    }

    private fun onPay() {
        val s = _state.value
        if (s.launching) return
        val error = validationError()
        if (error != null) {
            _state.update { it.copy(validation = error) }
            return
        }
        when {
            s.apps.isEmpty() -> _state.update { it.copy(validation = "No UPI app found. Install GPay, PhonePe or Paytm.") }
            s.defaultApp != null -> startPayment(s.defaultApp.packageName, remember = false)
            s.apps.size == 1 -> startPayment(s.apps.first().packageName, remember = false)
            else -> _state.update { it.copy(showAppPicker = true) }
        }
    }

    private fun startPayment(packageName: String, remember: Boolean) {
        val s = _state.value
        val target = payload ?: return
        val amount = s.amountPaise ?: return
        val category = s.selectedCategory ?: return
        if (s.launching) return
        _state.update { it.copy(launching = true) }
        viewModelScope.launch {
            if (remember) {
                settingsRepo.setDefaultUpi(packageName)
                _state.update { it.copy(defaultApp = it.apps.firstOrNull { app -> app.packageName == packageName }) }
            }
            var contactId = s.contactId
            if (category.kind == CategoryKind.LENT && contactId == null) {
                contactId = contacts.findByVpa(target.vpa)?.id ?: contacts.save(0, s.lentName, target.vpa, null)
                _state.update { it.copy(contactId = contactId) }
            }
            val note = s.note.trim().ifEmpty { null }
            val id = payments.createPending(
                NewAppPayment(
                    amountPaise = amount,
                    payeeVpa = target.vpa,
                    payeeName = target.name ?: s.payeeName,
                    categoryId = category.id,
                    note = note,
                    contactId = contactId,
                    upiApp = packageName,
                    txnRef = PaymentRepository.newTxnRef(),
                    dueAt = if (category.kind == CategoryKind.LENT) s.dueAt else null,
                ),
            )
            savedState[KEY_PENDING_ID] = id
            val upiNote = note ?: if (category.kind == CategoryKind.LENT) "Lent" else category.name
            val link = if (payingSavedPerson) {
                UpiUri.buildPersonLink(target.vpa, target.name, amount, upiNote)
            } else {
                UpiUri.buildPayLink(target, amount, upiNote)
            }
            lockManager.markExternalLaunch()
            _effects.send(PayEffect.LaunchUpi(link, packageName))
        }
    }

    /** Back from the UPI app: save what it said and leave quietly. Never asks anything. */
    private fun onReturned(response: String?) {
        val id = savedState.get<Long>(KEY_PENDING_ID)
        savedState.remove<Long>(KEY_PENDING_ID)
        if (id != null) {
            val result = UpiResponseParser.parse(response)
            appScope.launch {
                payments.applyUpiResult(id, result)
                syncScheduler.syncSoon(delaySeconds = 10)
            }
        }
        _state.update { it.copy(launching = false) }
        viewModelScope.launch { _effects.send(PayEffect.Finished) }
    }

    private fun onLaunchFailed() {
        val id = savedState.get<Long>(KEY_PENDING_ID)
        savedState.remove<Long>(KEY_PENDING_ID)
        viewModelScope.launch {
            id?.let { payments.getById(it)?.let { p -> payments.delete(p) } }
            _state.update { it.copy(launching = false, validation = "Couldn't open that UPI app. Try another one.", showAppPicker = it.apps.size > 1) }
        }
    }

    companion object {
        private const val KEY_PENDING_ID = "pending_payment_id"
        private const val UPI_LIMIT_PAISE = 1_00_000L * 100
        private val AMOUNT_PATTERN = Regex("^\\d{0,6}(\\.\\d{0,2})?$")
    }
}
