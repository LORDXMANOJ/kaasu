package com.manoj.kaasu.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.manoj.kaasu.core.PinHasher
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.security.AppLockManager
import com.manoj.kaasu.security.BiometricHelper
import com.manoj.kaasu.ui.components.PinDots
import com.manoj.kaasu.ui.components.PinPad
import com.manoj.kaasu.ui.util.findActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

const val PIN_LENGTH = 4

data class LockState(
    val pin: String = "",
    val isError: Boolean = false,
    val isChecking: Boolean = false,
    val failedAttempts: Int = 0,
    val cooldownSeconds: Int = 0,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val lockManager: AppLockManager,
) : ViewModel() {
    private val _state = MutableStateFlow(LockState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    fun onDigit(digit: Char) {
        val s = _state.value
        if (s.isChecking || s.cooldownSeconds > 0 || s.pin.length >= PIN_LENGTH) return
        val pin = s.pin + digit
        _state.update { it.copy(pin = pin, isError = false) }
        if (pin.length == PIN_LENGTH) verify(pin)
    }

    fun onBackspace() = _state.update { it.copy(pin = it.pin.dropLast(1), isError = false) }

    fun onBiometricSuccess() = unlock()

    private fun verify(pin: String) = viewModelScope.launch {
        _state.update { it.copy(isChecking = true) }
        val current = settings.current()
        val ok = withContext(Dispatchers.Default) {
            val hash = current.pinHash
            val salt = current.pinSalt
            hash != null && salt != null && PinHasher.isMatch(pin, salt, hash)
        }
        if (ok) {
            unlock()
            return@launch
        }
        val attempts = _state.value.failedAttempts + 1
        _state.update { it.copy(isChecking = false, isError = true, failedAttempts = attempts) }
        delay(450)
        _state.update { it.copy(pin = "") }
        if (attempts % MAX_ATTEMPTS == 0) startCooldown()
    }

    private suspend fun startCooldown() {
        for (left in COOLDOWN_SECONDS downTo 1) {
            _state.update { it.copy(cooldownSeconds = left) }
            delay(1000)
        }
        _state.update { it.copy(cooldownSeconds = 0, isError = false) }
    }

    private fun unlock() {
        _state.value = LockState()
        lockManager.unlock()
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val COOLDOWN_SECONDS = 30
    }
}

@Composable
fun LockScreen(biometricEnabled: Boolean, vm: LockViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val canUseBiometric = biometricEnabled && BiometricHelper.isAvailable(context)
    val showBiometric = { activity?.let { BiometricHelper.prompt(it, onSuccess = vm::onBiometricSuccess) } }

    LaunchedEffect(canUseBiometric) { if (canUseBiometric) showBiometric() }
    BackHandler { activity?.moveTaskToBack(true) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { /* swallow touches meant for the app underneath */ },
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Welcome back", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    state.cooldownSeconds > 0 -> "Too many tries. Wait ${state.cooldownSeconds}s"
                    state.isError -> "Wrong PIN, try again"
                    else -> "Enter your $PIN_LENGTH-digit PIN"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isError || state.cooldownSeconds > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            PinDots(length = state.pin.length, max = PIN_LENGTH, isError = state.isError)
            Spacer(Modifier.height(44.dp))
            PinPad(
                onDigit = vm::onDigit,
                onBackspace = vm::onBackspace,
                extraKey = if (canUseBiometric) {
                    {
                        IconButton(onClick = { showBiometric() }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = "Unlock with fingerprint", modifier = Modifier.size(34.dp))
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}
