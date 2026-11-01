package com.manoj.kaasu.domain

import com.manoj.kaasu.core.TimeSlot
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Direction

data class OrderedCategories(val suggested: List<Category>, val others: List<Category>) {
    val all: List<Category> get() = suggested + others
}

/** Fixed, predictable order: this time of day's picks on top, everything else below, "Other" last. */
object CategoryOrdering {
    private val hiddenFromSpending = setOf(CategoryKind.REPAYMENT, CategoryKind.INCOME)

    fun forPayment(categories: List<Category>, slot: TimeSlot, toPerson: Boolean): OrderedCategories {
        val selectable = categories.filter { !it.archived && it.kind !in hiddenFromSpending }
        val lent = selectable.filter { it.kind == CategoryKind.LENT }
        val pinned = selectable.filter { it.kind != CategoryKind.LENT && it.pinnedSlots and slot.bit != 0 }
            .sortedBy { it.sortOrder }
        val suggested = if (toPerson) lent + pinned else pinned
        val others = selectable.filter { it !in suggested }
            .sortedWith(compareBy({ it.kind == CategoryKind.OTHER }, { it.sortOrder }))
        return OrderedCategories(suggested, others)
    }

    fun forIncoming(categories: List<Category>): List<Category> =
        categories.filter { !it.archived && it.kind in hiddenFromSpending }.sortedBy { it.sortOrder }

    fun forDirection(categories: List<Category>, direction: Direction, slot: TimeSlot, toPerson: Boolean): List<Category> =
        if (direction == Direction.IN) forIncoming(categories) else forPayment(categories, slot, toPerson).all
}
