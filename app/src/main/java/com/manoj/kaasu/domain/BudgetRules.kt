package com.manoj.kaasu.domain

import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.prefs.UserSettings
import java.time.LocalDate

data class BudgetCheck(
    val budgetPaise: Long,
    val spentPaise: Long,
    val afterPaise: Long,
    val countsTowardBudget: Boolean,
    val categoryLimitPaise: Long?,
    val categorySpentPaise: Long,
) {
    val overByPaise: Long get() = if (countsTowardBudget) (afterPaise - budgetPaise).coerceAtLeast(0) else 0
    val categoryOverByPaise: Long
        get() = categoryLimitPaise?.let { (categorySpentPaise - it).coerceAtLeast(0) } ?: 0
    val hasWarning: Boolean get() = overByPaise > 0 || categoryOverByPaise > 0
}

object BudgetRules {
    /** Money that leaves your pocket for yourself. Lending is tracked separately unless you opt in. */
    fun isSpend(p: Payment, categories: Map<Long, Category>, countLent: Boolean): Boolean =
        p.direction == Direction.OUT && p.status != PaymentStatus.FAILED &&
            (countLent || categories[p.categoryId]?.kind != CategoryKind.LENT)

    fun spentOn(day: LocalDate, payments: List<Payment>, categories: Map<Long, Category>, countLent: Boolean): Long =
        payments.filter { Dates.toLocalDate(it.createdAt) == day && isSpend(it, categories, countLent) }
            .sumOf { it.amountPaise }

    fun check(
        amountPaise: Long,
        category: Category?,
        todayPayments: List<Payment>,
        categories: Map<Long, Category>,
        settings: UserSettings,
        today: LocalDate = Dates.today(),
    ): BudgetCheck {
        val spent = spentOn(today, todayPayments, categories, settings.countLentInBudget)
        val counts = settings.countLentInBudget || category?.kind != CategoryKind.LENT
        val categorySpent = if (category == null) 0 else todayPayments
            .filter { Dates.toLocalDate(it.createdAt) == today && it.categoryId == category.id && it.status != PaymentStatus.FAILED && it.direction == Direction.OUT }
            .sumOf { it.amountPaise } + amountPaise
        return BudgetCheck(
            budgetPaise = settings.budgetForDay(today.toEpochDay()),
            spentPaise = spent,
            afterPaise = if (counts) spent + amountPaise else spent,
            countsTowardBudget = counts,
            categoryLimitPaise = category?.dailyLimitPaise,
            categorySpentPaise = categorySpent,
        )
    }
}
