package com.manoj.kaasu.ui.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.security.BiometricHelper
import com.manoj.kaasu.ui.common.PinSetup
import com.manoj.kaasu.ui.components.BudgetRing
import com.manoj.kaasu.ui.components.PrimaryButton
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val onEvent = vm::onEvent
    BackHandler(enabled = state.step > 0) { onEvent(OnboardingEvent.Back) }

    Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            StepDots(current = state.step, total = OnboardingState.STEPS, modifier = Modifier.padding(start = 24.dp, top = 20.dp))
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(280)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(280)))
                        .togetherWith(slideOutHorizontally(tween(200)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)))
                },
                modifier = Modifier.weight(1f),
                label = "onboardingStep",
            ) { step ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    when (step) {
                        0 -> WelcomeStep(state, onEvent)
                        1 -> BudgetStep(state, onEvent)
                        2 -> SmsStep(state, onEvent)
                        else -> LockStep(state, onEvent)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                val last = state.step == OnboardingState.STEPS - 1
                PrimaryButton(
                    text = when {
                        last -> "Start using Kaasu"
                        state.step == 2 && !state.smsGranted -> "Skip for now"
                        else -> "Continue"
                    },
                    enabled = state.canContinue,
                    loading = state.finishing,
                    onClick = { onEvent(if (last) OnboardingEvent.Finish else OnboardingEvent.Next) },
                )
            }
        }
    }
}

@Composable
private fun StepDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (index == current) 28.dp else 12.dp)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}

@Composable
private fun ColumnScope.WelcomeStep(state: OnboardingState, onEvent: (OnboardingEvent) -> Unit) {
    Spacer(Modifier.height(24.dp))
    BudgetRing(
        progress = 0.68f,
        progressColor = KaasuTheme.colors.success,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        size = 120.dp,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text("₹", style = AmountStyles.hero, color = MaterialTheme.colorScheme.onSurface)
    }
    Spacer(Modifier.height(28.dp))
    Text("Vanakkam! I'm Kaasu", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Scan, say what it's for, then pay in your UPI app. Kaasu keeps the history, your daily budget and a weekly report card.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    OutlinedTextField(
        value = state.name,
        onValueChange = { onEvent(OnboardingEvent.NameChanged(it)) },
        label = { Text("What should I call you?") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BudgetStep(state: OnboardingState, onEvent: (OnboardingEvent) -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text("How much can you spend in a day?", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Before a payment takes you over this, Kaasu shows a warning. You can still pay, it's your call.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    OutlinedTextField(
        value = state.budgetText,
        onValueChange = { v -> if (v.isEmpty() || v.all { it.isDigit() }) onEvent(OnboardingEvent.BudgetChanged(v)) },
        prefix = { Text("₹ ", style = AmountStyles.large) },
        textStyle = AmountStyles.large,
        suffix = { Text("per day", style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(100, 150, 200, 300, 500, 1000).forEach { rupees ->
            FilterChip(
                selected = state.budgetText == rupees.toString(),
                onClick = { onEvent(OnboardingEvent.BudgetChanged(rupees.toString())) },
                label = { Text("₹$rupees", style = MaterialTheme.typography.titleSmall) },
                modifier = Modifier.height(44.dp),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "You can change it any time, or raise just today's budget from Home.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ColumnScope.SmsStep(state: OnboardingState, onEvent: (OnboardingEvent) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onEvent(OnboardingEvent.SmsPermissionResult(result[Manifest.permission.READ_SMS] == true))
    }
    Spacer(Modifier.height(16.dp))
    IconBadge(Icons.Rounded.Sms)
    Spacer(Modifier.height(20.dp))
    Text("Let Kaasu read bank SMS?", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Benefit(Icons.Rounded.Verified, "Confirms every payment automatically with the bank reference number. No questions after you pay.")
    Benefit(Icons.Rounded.AccountBalance, "Shows your bank balance from your bank's messages.")
    Benefit(Icons.AutoMirrored.Rounded.ManageSearch, "Catches payments you make straight from GPay or PhonePe, so nothing is missed.")
    Benefit(Icons.Rounded.Shield, "Only bank transaction messages are used, and nothing ever leaves your phone.")
    Spacer(Modifier.height(20.dp))
    if (state.smsGranted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = KaasuTheme.colors.success)
            Spacer(Modifier.width(8.dp))
            Text("SMS access is on", style = MaterialTheme.typography.titleSmall, color = KaasuTheme.colors.success)
        }
    } else {
        OutlinedButton(
            onClick = { launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Rounded.Sms, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Allow SMS access")
        }
    }
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = state.balanceText,
        onValueChange = { v -> if (v.isEmpty() || Money.parseToPaise(v) != null || v.endsWith(".")) onEvent(OnboardingEvent.BalanceChanged(v)) },
        label = { Text("Bank balance right now (optional)") },
        supportingText = { Text("Kaasu keeps it updated from your payments and bank SMS.") },
        prefix = { Text("₹ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.LockStep(state: OnboardingState, onEvent: (OnboardingEvent) -> Unit) {
    val context = LocalContext.current
    Spacer(Modifier.height(8.dp))
    if (state.pin == null) {
        Text("Lock Kaasu with a PIN?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "It never asks right after you pay, only when you open Kaasu later. You can skip this.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        PinSetup(onComplete = { onEvent(OnboardingEvent.PinChosen(it)) }, modifier = Modifier.fillMaxWidth())
    } else {
        Spacer(Modifier.height(24.dp))
        IconBadge(Icons.Rounded.Lock)
        Spacer(Modifier.height(20.dp))
        Text("PIN is set", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Kaasu will ask for it when you open the app after a while.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (BiometricHelper.isAvailable(context)) {
            Spacer(Modifier.height(24.dp))
            Surface(shape = MaterialTheme.shapes.large, color = androidx.compose.ui.graphics.Color.Transparent) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Unlock with fingerprint too", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = state.biometric, onCheckedChange = { onEvent(OnboardingEvent.BiometricChanged(it)) })
                }
            }
        }
    }
    if (state.pin == null) {
        TextButton(onClick = { onEvent(OnboardingEvent.Finish) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Skip, no lock")
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        Modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
    }
}
