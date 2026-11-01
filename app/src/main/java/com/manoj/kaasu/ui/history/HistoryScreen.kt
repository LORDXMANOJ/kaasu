package com.manoj.kaasu.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.ui.components.EmptyState
import com.manoj.kaasu.ui.components.KCard
import com.manoj.kaasu.ui.components.TxnRow
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.theme.AmountStyles
import com.manoj.kaasu.ui.theme.KaasuTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(navController: NavHostController, vm: HistoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            "History",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(start = 20.dp, top = 16.dp, end = 20.dp)
                .semantics { heading() },
        )
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Search name, UPI ID, amount, UTR…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(HistoryFilter.entries) { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { vm.onFilterChange(filter) },
                    label = { Text(filter.label) },
                    modifier = Modifier.height(40.dp),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + com.manoj.kaasu.ui.navigation.LocalBarInset.current),
        ) {
            item(key = "summary") {
                KCard(Modifier.padding(16.dp)) {
                    Text(state.monthLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Paid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(state.monthOutPaise), style = AmountStyles.large)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Received", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(state.monthInPaise), style = AmountStyles.large, color = KaasuTheme.colors.success)
                        }
                    }
                }
            }
            if (!state.loading && state.groups.isEmpty()) {
                item(key = "empty") {
                    if (query.isNotEmpty() || state.filter != HistoryFilter.ALL) {
                        EmptyState(Icons.Rounded.SearchOff, "Nothing found", "Try another word or filter.")
                    } else {
                        EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "No payments yet", "Payments you make with Kaasu, and ones found in bank SMS, show up here.")
                    }
                }
            }
            state.groups.forEach { group ->
                stickyHeader(key = "h-${group.date}") {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(group.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (group.inPaise > 0) {
                                Text("+${Money.format(group.inPaise)}", style = AmountStyles.small, color = KaasuTheme.colors.success)
                                Spacer(Modifier.width(10.dp))
                            }
                            if (group.outPaise > 0) {
                                Text("−${Money.format(group.outPaise)}", style = AmountStyles.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                items(group.items, key = { it.id }) { txn ->
                    TxnRow(txn, onClick = {
                        focusManager.clearFocus()
                        navController.navigate(Routes.txn(txn.id))
                    })
                }
            }
        }
    }
}
