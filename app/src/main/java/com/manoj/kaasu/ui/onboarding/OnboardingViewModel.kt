package com.manoj.kaasu.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.core.PinHasher
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.sms.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OnboardingState(
    val step: Int = 0,
    val name: String = "",
    val budgetText: String = "200",
    val smsGranted: Boolean = false,
    val balanceText: String = "",
    val pin: String? = null,
    val biometric: Boolean = false,
    val finishing: Boolean = false,
) {
    val budgetPaise: Long? get() = Money.parseToPaise(budgetText)?.takeIf { it > 0 }
    val canContinue: Boolean
        get() = when (step) {
            0 -> name.isNotBlank()
            1 -> budgetPaise != null
            else -> true
        }

    companion object {
        const val STEPS = 4
    }
}

sealed interface OnboardingEvent {
    data class NameChanged(val value: String) : OnboardingEvent
    data class BudgetChanged(val value: String) : OnboardingEvent
    data class BalanceChanged(val value: String) : OnboardingEvent
    data class SmsPermissionResult(val granted: Boolean) : OnboardingEvent
    data class PinChosen(val pin: String) : OnboardingEvent
    data class BiometricChanged(val enabled: Boolean) : OnboardingEvent
    data object Next : OnboardingEvent
    data object Back : OnboardingEvent
    data object Finish : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.NameChanged -> _state.update { it.copy(name = event.value.take(30)) }
            is OnboardingEvent.BudgetChanged -> _state.update { it.copy(budgetText = event.value.take(9)) }
            is OnboardingEvent.BalanceChanged -> _state.update { it.copy(balanceText = event.value.take(12)) }
            is OnboardingEvent.SmsPermissionResult -> _state.update { it.copy(smsGranted = event.granted) }
            is OnboardingEvent.PinChosen -> _state.update { it.copy(pin = event.pin) }
            is OnboardingEvent.BiometricChanged -> _state.update { it.copy(biometric = event.enabled) }
            OnboardingEvent.Next -> _state.update {
                if (it.canContinue) it.copy(step = (it.step + 1).coerceAtMost(OnboardingState.STEPS - 1)) else it
            }
            OnboardingEvent.Back -> _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
            OnboardingEvent.Finish -> finish()
        }
    }

    private fun finish() {
        val s = _state.value
        if (s.finishing) return
        _state.update { it.copy(finishing = true) }
        viewModelScope.launch {
            if (s.smsGranted) settings.setSmsSync(true)
            Money.parseToPaise(s.balanceText)?.let { settings.setOpeningBalance(it) }
            s.pin?.let { pin ->
                val salt = PinHasher.newSalt()
                val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
                settings.setPin(hash, salt)
                settings.setBiometric(s.biometric)
            }
            settings.completeOnboarding(s.name, s.budgetPaise ?: DEFAULT_BUDGET)
            syncScheduler.syncSoon()
        }
    }

    companion object {
        private const val DEFAULT_BUDGET = 20_000L
    }
}
