package com.manoj.kaasu.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.ui.components.CategoryIcon
import com.manoj.kaasu.ui.components.PrimaryButton
import com.manoj.kaasu.ui.theme.categoryColor

@Composable
fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = category.name,
) {
    val color = categoryColor(category.colorIndex)
    val haptics = LocalHapticFeedback.current
    val background by animateColorAsState(
        if (selected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "chipBg",
    )
    val borderColor by animateColorAsState(
        if (selected) color else MaterialTheme.colorScheme.outlineVariant,
        label = "chipBorder",
    )
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(50),
        color = background,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIcon(category.icon, category.colorIndex, size = 34.dp)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Check, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Two groups: what you usually pay for at this time of day, then everything else. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChipGrid(
    suggestedTitle: String?,
    suggested: List<Category>,
    others: List<Category>,
    selectedId: Long?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
    labelFor: (Category) -> String = { it.name },
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (suggested.isNotEmpty()) {
            if (suggestedTitle != null) {
                Text(suggestedTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggested.forEach { CategoryChip(it, it.id == selectedId, { onSelect(it) }, label = labelFor(it)) }
            }
        }
        if (others.isNotEmpty()) {
            if (suggested.isNotEmpty()) {
                Spacer(Modifier.size(2.dp))
                Text("Everything else", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { CategoryChip(it, it.id == selectedId, { onSelect(it) }, label = labelFor(it)) }
            }
        }
    }
}

/** Bottom sheet to tag (or re-tag) a payment after the fact. */
@Composable
fun CategoryPickerSheet(
    title: String,
    subtitle: String?,
    suggestedTitle: String?,
    suggested: List<Category>,
    others: List<Category>,
    initialCategoryId: Long?,
    initialNote: String?,
    onSave: (categoryId: Long, note: String?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var selectedId by rememberSaveable { mutableStateOf(initialCategoryId) }
    var note by rememberSaveable { mutableStateOf(initialNote.orEmpty()) }
    val all = suggested + others
    val isOther = all.firstOrNull { it.id == selectedId }?.kind == CategoryKind.OTHER
    val canSave = selectedId != null && (!isOther || note.isNotBlank())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            CategoryChipGrid(
                suggestedTitle = suggestedTitle,
                suggested = suggested,
                others = others,
                selectedId = selectedId,
                onSelect = { selectedId = it.id },
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(80) },
                label = { Text(if (isOther) "What was it for?" else "Note (optional)") },
                isError = isOther && note.isBlank(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = "Save",
                enabled = canSave,
                onClick = { selectedId?.let { onSave(it, note.trim().ifEmpty { null }) } },
            )
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Not a real payment? Delete it", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
