package com.manoj.kaasu.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manoj.kaasu.ui.theme.AmountStyles

@Composable
fun PinDots(length: Int, max: Int, isError: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "$length of $max digits entered" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(max) { index ->
            val filled = index < length
            val color by animateColorAsState(
                when {
                    isError -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                label = "pinDot",
            )
            Box(
                Modifier
                    .size(16.dp)
                    .background(if (filled || isError) color else MaterialTheme.colorScheme.surface, CircleShape)
                    .border(2.dp, color, CircleShape),
            )
        }
    }
}

/** Number pad. [extraKey] fills the bottom-left slot (e.g. fingerprint). */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    extraKey: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val rows = listOf("123", "456", "789")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                row.forEach { digit ->
                    PadKey(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDigit(digit) }, label = digit.toString()) {
                        Text(digit.toString(), style = AmountStyles.large)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) { extraKey?.invoke() }
            PadKey(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDigit('0') }, label = "0") {
                Text("0", style = AmountStyles.large)
            }
            PadKey(onClick = onBackspace, label = "Delete", subtle = true) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null)
            }
        }
    }
}

private val KEY_SIZE = 74.dp

@Composable
private fun PadKey(onClick: () -> Unit, label: String, subtle: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (subtle) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(KEY_SIZE)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
