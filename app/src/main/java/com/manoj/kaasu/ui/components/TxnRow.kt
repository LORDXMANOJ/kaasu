package com.manoj.kaasu.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.ui.theme.KaasuTheme
import com.manoj.kaasu.ui.theme.categoryColor

@Immutable
data class TxnUi(
    val id: Long,
    val title: String,
    val subtitle: String,
    val amountPaise: Long,
    val direction: Direction,
    val status: PaymentStatus,
    val icon: String,
    val colorIndex: Int,
    val isUntagged: Boolean,
    val createdAt: Long,
    val categoryName: String?,
    val searchText: String,
)

fun Payment.toUi(categories: Map<Long, Category>, contacts: Map<Long, Contact>): TxnUi {
    val category = categories[categoryId]
    val contact = contacts[contactId]
    val untagged = direction == Direction.OUT && category == null
    val title = contact?.name
        ?: payeeName?.takeIf { it.isNotBlank() }
        ?: payeeVpa?.takeIf { it.isNotBlank() }
        ?: category?.name
        ?: if (direction == Direction.IN) "Money received" else "Payment"
    val categoryLabel = when {
        category?.kind == CategoryKind.LENT && contact != null -> "Lent"
        category != null -> category.name
        untagged -> "Untagged"
        else -> null
    }
    val subtitle = listOfNotNull(categoryLabel, note?.takeIf { it.isNotBlank() }, if (isCash) "Cash" else null, Dates.time(createdAt))
        .joinToString(" · ")
    return TxnUi(
        id = id,
        title = title,
        subtitle = subtitle,
        amountPaise = amountPaise,
        direction = direction,
        status = status,
        icon = category?.icon ?: if (direction == Direction.IN) "income" else "untagged",
        colorIndex = category?.colorIndex ?: if (direction == Direction.IN) 14 else 13,
        isUntagged = untagged,
        createdAt = createdAt,
        categoryName = category?.name,
        searchText = listOfNotNull(title, payeeVpa, category?.name, note, bankRef, upiTxnId, txnRef, Money.format(amountPaise))
            .joinToString(" ").lowercase(),
    )
}

@Composable
fun TxnRow(txn: TxnUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val verb = if (txn.direction == Direction.IN) "Received" else "Paid"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$verb ${Money.format(txn.amountPaise)}, ${txn.title}, ${txn.subtitle}, ${txn.status.name.lowercase()}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(
            icon = txn.icon,
            colorIndex = txn.colorIndex,
            tint = if (txn.isUntagged) KaasuTheme.colors.warning else categoryColor(txn.colorIndex),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(txn.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                txn.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (txn.isUntagged) KaasuTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AmountText(paise = txn.amountPaise, direction = txn.direction, status = txn.status)
            if (txn.status != PaymentStatus.SUCCESS) StatusPill(txn.status)
        }
    }
}
