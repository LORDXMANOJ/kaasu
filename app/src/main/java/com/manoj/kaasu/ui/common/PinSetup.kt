package com.manoj.kaasu.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.manoj.kaasu.ui.components.PinDots
import com.manoj.kaasu.ui.components.PinPad
import com.manoj.kaasu.ui.lock.PIN_LENGTH
import kotlinx.coroutines.delay

/** Enter a new PIN twice. Calls [onComplete] with the confirmed PIN. */
@Composable
fun PinSetup(onComplete: (String) -> Unit, modifier: Modifier = Modifier) {
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var mismatch by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(input) {
        if (input.length < PIN_LENGTH) return@LaunchedEffect
        delay(150)
        val saved = first
        when {
            saved == null -> {
                first = input
                input = ""
            }
            saved == input -> onComplete(input)
            else -> {
                mismatch = true
                delay(500)
                first = null
                input = ""
            }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            if (first == null) "Choose a $PIN_LENGTH-digit PIN" else "Enter it once more",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (mismatch) "PINs didn't match. Start again." else "You'll use it to open Kaasu",
            style = MaterialTheme.typography.bodyMedium,
            color = if (mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PinDots(length = input.length, max = PIN_LENGTH, isError = mismatch && input.isEmpty() && first == null)
        Spacer(Modifier.height(32.dp))
        PinPad(
            onDigit = { if (input.length < PIN_LENGTH) { mismatch = false; input += it } },
            onBackspace = { input = input.dropLast(1) },
        )
    }
}
