package com.manoj.kaasu.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.core.TimeSlot
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.MealType
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.components.CategoryIcon
import com.manoj.kaasu.ui.components.CategoryIcons
import com.manoj.kaasu.ui.components.PrimaryButton
import com.manoj.kaasu.ui.components.SectionHeader
import com.manoj.kaasu.ui.theme.CategoryColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val systemKinds = setOf(CategoryKind.REPAYMENT, CategoryKind.INCOME)

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repo: CategoryRepository) : ViewModel() {
    val categories: StateFlow<List<Category>> = repo.categories
        .map { list -> list.filter { it.kind !in systemKinds } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(category: Category) = viewModelScope.launch { repo.save(category) }
    fun move(category: Category, up: Boolean) = viewModelScope.launch { repo.move(category, up) }
    fun setArchived(category: Category, archived: Boolean) = viewModelScope.launch { repo.archive(category, archived) }
}

@Composable
fun CategoriesScreen(navController: NavHostController, vm: CategoriesViewModel = hiltViewModel()) {
    val all by vm.categories.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val active = all.filter { !it.archived }
    val hidden = all.filter { it.archived }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            BackTopBar(title = "Categories", onBack = { navController.popBackStack() })
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp),
            ) {
                item(key = "hint") {
                    Text(
                        "Use the arrows to change the order. A category marked for a time of day jumps to the top at that time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(active, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        canMoveUp = category != active.first(),
                        canMoveDown = category != active.last(),
                        onMove = { up -> vm.move(category, up) },
                        onClick = { editingId = category.id },
                    )
                }
                if (hidden.isNotEmpty()) {
                    item(key = "hiddenHeader") { SectionHeader("Hidden", Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                    items(hidden, key = { it.id }) { category ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(category.icon, category.colorIndex, size = 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(category.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.setArchived(category, false) }) { Text("Show again") }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New category") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }

    val editing = all.firstOrNull { it.id == editingId }
    if (editing != null || creating) {
        CategoryEditSheet(
            initial = editing,
            onSave = {
                vm.save(it)
                editingId = null
                creating = false
            },
            onHide = editing?.takeIf { it.kind == CategoryKind.NORMAL || it.kind == CategoryKind.MEAL }?.let { c ->
                {
                    vm.setArchived(c, true)
                    editingId = null
                }
            },
            onDismiss = {
                editingId = null
                creating = false
            },
        )
    }
}

@Composable
private fun CategoryRow(category: Category, canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category.icon, category.colorIndex, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val tags = buildList {
                TimeSlot.fromMask(category.pinnedSlots).takeIf { it.isNotEmpty() }?.let { add("Top in " + it.joinToString { s -> s.label.lowercase() }) }
                category.mealType?.let { add(it.label) }
                if (category.isImpulse) add("Treat")
                if (category.kind == CategoryKind.LENT) add("Tracks lending")
                if (category.kind == CategoryKind.OTHER) add("Asks you to type")
                category.dailyLimitPaise?.let { add("Limit ${Money.format(it)}/day") }
            }
            if (tags.isNotEmpty()) {
                Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${category.name} up")
        }
        IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${category.name} down")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditSheet(initial: Category?, onSave: (Category) -> Unit, onHide: (() -> Unit)?, onDismiss: () -> Unit) {
    val editableKind = initial == null || initial.kind == CategoryKind.NORMAL || initial.kind == CategoryKind.MEAL
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(initial?.icon ?: "bag") }
    var color by rememberSaveable { mutableStateOf(initial?.colorIndex ?: 6) }
    var pinned by rememberSaveable { mutableStateOf(initial?.pinnedSlots ?: 0) }
    var meal by rememberSaveable { mutableStateOf(initial?.mealType) }
    var impulse by rememberSaveable { mutableStateOf(initial?.isImpulse ?: false) }
    var limitText by rememberSaveable { mutableStateOf(initial?.dailyLimitPaise?.let { Money.toInputText(it) }.orEmpty()) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(icon, color, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Text(if (initial == null) "New category" else "Edit category", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryIcons.pickable.forEach { key ->
                    val selected = key == icon
                    Box(
                        Modifier
                            .size(48.dp)
                            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                            .clickable { icon = key }
                            .semantics {
                                this.selected = selected
                                contentDescription = "Icon $key"
                            },
                        contentAlignment = Alignment.Center,
                    ) { CategoryIcon(key, color, size = 40.dp) }
                }
            }
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryColors.forEachIndexed { index, c ->
                    val selected = index == color
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(c, CircleShape)
                            .clickable { color = index }
                            .semantics {
                                this.selected = selected
                                contentDescription = "Colour ${index + 1}"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }
            Text("Show on top in the…", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeSlot.entries.forEach { slot ->
                    val on = pinned and slot.bit != 0
                    FilterChip(
                        selected = on,
                        onClick = { pinned = if (on) pinned and slot.bit.inv() else pinned or slot.bit },
                        label = { Text(slot.label) },
                        modifier = Modifier.height(40.dp),
                    )
                }
            }
            if (editableKind) {
                Text("Is it a meal?", style = MaterialTheme.typography.labelLarge)
                val options = listOf<Pair<MealType?, String>>(null to "No") + MealType.entries.map { it to it.label }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (type, label) ->
                        SegmentedButton(
                            selected = meal == type,
                            onClick = { meal = type },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Counts as a snack or treat", style = MaterialTheme.typography.titleSmall)
                        Text("Lowers the snacks mark in your weekly report", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = impulse, onCheckedChange = { impulse = it })
                }
            }
            OutlinedTextField(
                value = limitText,
                onValueChange = { v -> if (v.isEmpty() || v.all { it.isDigit() }) limitText = v.take(6) },
                label = { Text("Daily limit (optional)") },
                supportingText = { Text("Warns before you pay more than this on it in a day") },
                prefix = { Text("₹ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = "Save",
                enabled = name.isNotBlank(),
                onClick = {
                    val kind = when {
                        !editableKind -> initial!!.kind
                        meal != null -> CategoryKind.MEAL
                        else -> CategoryKind.NORMAL
                    }
                    val base = initial ?: Category(name = "", icon = icon, colorIndex = color, kind = kind, sortOrder = 0)
                    onSave(
                        base.copy(
                            name = name.trim(),
                            icon = icon,
                            colorIndex = color,
                            kind = kind,
                            mealType = if (editableKind) meal else base.mealType,
                            isImpulse = if (editableKind) impulse else base.isImpulse,
                            pinnedSlots = pinned,
                            dailyLimitPaise = Money.parseToPaise(limitText)?.takeIf { it > 0 },
                        ),
                    )
                },
            )
            if (onHide != null) {
                TextButton(onClick = onHide, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Hide this category", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
