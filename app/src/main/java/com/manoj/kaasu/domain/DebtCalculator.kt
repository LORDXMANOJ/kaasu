package com.manoj.kaasu.domain

import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentStatus

data class DebtSummary(val lentPaise: Long, val returnedPaise: Long) {
    val outstandingPaise: Long get() = lentPaise - returnedPaise
}

object DebtCalculator {
    fun isLent(p: Payment, categories: Map<Long, Category>): Boolean =
        p.direction == Direction.OUT && p.status != PaymentStatus.FAILED &&
            categories[p.categoryId]?.kind == CategoryKind.LENT

    fun isRepayment(p: Payment, categories: Map<Long, Category>): Boolean =
        p.direction == Direction.IN && p.status != PaymentStatus.FAILED &&
            categories[p.categoryId]?.kind == CategoryKind.REPAYMENT

    fun summarize(payments: List<Payment>, categories: Map<Long, Category>): DebtSummary = DebtSummary(
        lentPaise = payments.filter { isLent(it, categories) }.sumOf { it.amountPaise },
        returnedPaise = payments.filter { isRepayment(it, categories) }.sumOf { it.amountPaise },
    )

    fun byContact(payments: List<Payment>, categories: Map<Long, Category>): Map<Long, DebtSummary> =
        payments.filter { it.contactId != null }
            .groupBy { it.contactId!! }
            .mapValues { (_, list) -> summarize(list, categories) }
}
