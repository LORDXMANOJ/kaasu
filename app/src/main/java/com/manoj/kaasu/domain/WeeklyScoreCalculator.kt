package com.manoj.kaasu.domain

import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.core.TimeSlot
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.MealLog
import com.manoj.kaasu.data.db.MealType
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.prefs.UserSettings
import java.time.LocalDate
import kotlin.math.roundToInt

data class ScoreInput(
    val weekStart: LocalDate,
    val today: LocalDate,
    val nowHour: Int,
    /** Must cover from the start of last month (or last week, whichever is earlier) up to the end of the week. */
    val payments: List<Payment>,
    val meals: List<MealLog>,
    val categories: Map<Long, Category>,
    val settings: UserSettings,
    val outstandingLentPaise: Long,
)

data class ScorePart(val key: String, val title: String, val score: Int, val max: Int, val detail: String) {
    val fraction: Float get() = if (max == 0) 0f else score.toFloat() / max
}

data class DayStat(val date: LocalDate, val spentPaise: Long, val budgetPaise: Long, val meals: Set<MealType>, val isFuture: Boolean)

data class CategoryStat(val category: Category?, val amountPaise: Long, val share: Float)

data class WeeklyReport(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val isCurrentWeek: Boolean,
    val total: Int,
    val grade: String,
    val headline: String,
    val parts: List<ScorePart>,
    val tips: List<String>,
    val spendPaise: Long,
    val lastWeekSpendPaise: Long,
    val lentPaise: Long,
    val receivedPaise: Long,
    val days: List<DayStat>,
    val categories: List<CategoryStat>,
    val slotSplit: Map<TimeSlot, Long>,
    val monthSpendPaise: Long,
    val lastMonthSpendPaise: Long,
    val outstandingLentPaise: Long,
    val daysElapsed: Int,
    val hasData: Boolean,
)

/**
 * Weekly marks out of 100:
 *  25 days within budget · 15 snacks/impulse share · 15 spend vs last week
 *  10 money lent · 20 three meals a day · 15 spend vs last month (savings)
 */
object WeeklyScoreCalculator {
    private const val BUDGET_MAX = 25
    private const val IMPULSE_MAX = 15
    private const val WEEK_MAX = 15
    private const val LENT_MAX = 10
    private const val MEALS_MAX = 20
    private const val MONTH_MAX = 15

    fun compute(input: ScoreInput): WeeklyReport {
        val weekEnd = input.weekStart.plusDays(6)
        val isCurrent = !input.today.isBefore(input.weekStart) && !input.today.isAfter(weekEnd)
        val lastDay = if (isCurrent) input.today else weekEnd
        val elapsed = if (input.today.isBefore(input.weekStart)) 0 else (lastDay.toEpochDay() - input.weekStart.toEpochDay() + 1).toInt()
        val cats = input.categories
        val valid = input.payments.filter { it.status != PaymentStatus.FAILED }

        fun inRange(p: Payment, from: LocalDate, to: LocalDate): Boolean {
            val d = Dates.toLocalDate(p.createdAt)
            return !d.isBefore(from) && !d.isAfter(to)
        }
        fun isLent(p: Payment) = p.direction == Direction.OUT && cats[p.categoryId]?.kind == CategoryKind.LENT
        fun isSpend(p: Payment) = p.direction == Direction.OUT && !isLent(p)

        val week = valid.filter { inRange(it, input.weekStart, weekEnd) }
        val spendList = week.filter(::isSpend)
        val spend = spendList.sumOf { it.amountPaise }
        val lent = week.filter(::isLent).sumOf { it.amountPaise }
        val received = week.filter { it.direction == Direction.IN && cats[it.categoryId]?.kind != CategoryKind.REPAYMENT }.sumOf { it.amountPaise }
        val impulse = spendList.filter { cats[it.categoryId]?.isImpulse == true }.sumOf { it.amountPaise }

        val lastWeekStart = input.weekStart.minusDays(7)
        val lastWeekSpend = valid.filter { isSpend(it) && inRange(it, lastWeekStart, lastWeekStart.plusDays((elapsed - 1).toLong().coerceAtLeast(0))) }
            .sumOf { it.amountPaise }

        val monthRef = lastDay
        val monthStart = Dates.monthStart(monthRef)
        val prevMonthStart = monthStart.minusMonths(1)
        val prevMonthRef = prevMonthStart.withDayOfMonth(minOf(monthRef.dayOfMonth, prevMonthStart.lengthOfMonth()))
        val monthSpend = valid.filter { isSpend(it) && inRange(it, monthStart, monthRef) }.sumOf { it.amountPaise }
        val lastMonthSpend = valid.filter { isSpend(it) && inRange(it, prevMonthStart, prevMonthRef) }.sumOf { it.amountPaise }

        val mealsByDay = mealsByDay(week, input.meals, cats)
        // Days before you started using Kaasu are not counted (unless they have data from a bank SMS import).
        val trackingStart = input.settings.untaggedSince.takeIf { it > 0 }?.let { Dates.toLocalDate(it) }
        val days = (0 until 7).map { offset ->
            val date = input.weekStart.plusDays(offset.toLong())
            val spent = BudgetRules.spentOn(date, week, cats, input.settings.countLentInBudget)
            val meals = mealsByDay[date].orEmpty()
            val beforeStart = trackingStart != null && date.isBefore(trackingStart) && spent == 0L && meals.isEmpty()
            DayStat(
                date = date,
                spentPaise = spent,
                budgetPaise = input.settings.budgetForDay(date.toEpochDay()),
                meals = meals,
                isFuture = date.isAfter(lastDay) || offset >= elapsed || beforeStart,
            )
        }
        val activeDays = days.filter { !it.isFuture }

        val parts = listOf(
            budgetPart(activeDays),
            impulsePart(spend, impulse),
            weekPart(spend, lastWeekSpend, elapsed),
            lentPart(spend, lent),
            mealsPart(activeDays, isCurrent, input),
            monthPart(monthSpend, lastMonthSpend),
        )
        val total = parts.sumOf { it.score }.coerceIn(0, 100)
        val categoryStats = spendList.groupBy { it.categoryId }
            .map { (id, list) -> CategoryStat(cats[id], list.sumOf { it.amountPaise }, if (spend == 0L) 0f else list.sumOf { it.amountPaise }.toFloat() / spend) }
            .sortedByDescending { it.amountPaise }
        val slotSplit = TimeSlot.entries.associateWith { slot ->
            spendList.filter { input.settings.slots.slotFor(it.createdAt) == slot }.sumOf { it.amountPaise }
        }
        return WeeklyReport(
            weekStart = input.weekStart,
            weekEnd = weekEnd,
            isCurrentWeek = isCurrent,
            total = total,
            grade = grade(total),
            headline = headline(total, week.isNotEmpty() || input.meals.isNotEmpty()),
            parts = parts,
            tips = tips(parts, activeDays, spend, impulse, lent, lastWeekSpend, monthSpend, lastMonthSpend, input.outstandingLentPaise, categoryStats),
            spendPaise = spend,
            lastWeekSpendPaise = lastWeekSpend,
            lentPaise = lent,
            receivedPaise = received,
            days = days,
            categories = categoryStats,
            slotSplit = slotSplit,
            monthSpendPaise = monthSpend,
            lastMonthSpendPaise = lastMonthSpend,
            outstandingLentPaise = input.outstandingLentPaise,
            daysElapsed = elapsed,
            hasData = week.isNotEmpty() || input.meals.isNotEmpty(),
        )
    }

    fun mealsByDay(payments: List<Payment>, logs: List<MealLog>, cats: Map<Long, Category>): Map<LocalDate, Set<MealType>> {
        val result = HashMap<LocalDate, MutableSet<MealType>>()
        payments.filter { it.direction == Direction.OUT && it.status != PaymentStatus.FAILED }.forEach { p ->
            cats[p.categoryId]?.mealType?.let { result.getOrPut(Dates.toLocalDate(p.createdAt)) { mutableSetOf() }.add(it) }
        }
        logs.forEach { result.getOrPut(LocalDate.ofEpochDay(it.epochDay)) { mutableSetOf() }.add(it.mealType) }
        return result
    }

    private fun ratioScore(max: Int, value: Float, best: Float, worst: Float): Int {
        val t = ((worst - value) / (worst - best)).coerceIn(0f, 1f)
        return (max * t).roundToInt()
    }

    private fun budgetPart(days: List<DayStat>): ScorePart {
        if (days.isEmpty()) return ScorePart("budget", "Within daily budget", BUDGET_MAX, BUDGET_MAX, "No days yet")
        val within = days.count { it.spentPaise <= it.budgetPaise }
        val score = (BUDGET_MAX * within.toFloat() / days.size).roundToInt()
        return ScorePart("budget", "Within daily budget", score, BUDGET_MAX, "$within of ${days.size} days")
    }

    private fun impulsePart(spend: Long, impulse: Long): ScorePart {
        val share = if (spend == 0L) 0f else impulse.toFloat() / spend
        val score = ratioScore(IMPULSE_MAX, share, 0f, 0.4f)
        return ScorePart("impulse", "Snacks & treats", score, IMPULSE_MAX, "${(share * 100).roundToInt()}% of spending")
    }

    private fun weekPart(spend: Long, lastWeek: Long, elapsed: Int): ScorePart {
        val title = "Spent less than last week"
        if (lastWeek == 0L) {
            val score = if (spend == 0L) WEEK_MAX else 10
            return ScorePart("week", title, score, WEEK_MAX, if (elapsed == 0) "No days yet" else "No spending last week to compare")
        }
        val ratio = spend.toFloat() / lastWeek
        val diff = spend - lastWeek
        val detail = if (diff <= 0) "${Money.format(-diff)} less" else "${Money.format(diff)} more"
        return ScorePart("week", title, ratioScore(WEEK_MAX, ratio, 0.9f, 1.3f), WEEK_MAX, detail)
    }

    private fun lentPart(spend: Long, lent: Long): ScorePart {
        val total = spend + lent
        val share = if (total == 0L) 0f else lent.toFloat() / total
        val detail = if (lent == 0L) "Nothing lent" else "${Money.format(lent)} lent"
        return ScorePart("lent", "Money lent to friends", ratioScore(LENT_MAX, share, 0f, 0.3f), LENT_MAX, detail)
    }

    private fun mealsPart(days: List<DayStat>, isCurrent: Boolean, input: ScoreInput): ScorePart {
        var eaten = 0
        var expected = 0
        days.forEach { day ->
            val count = day.meals.size.coerceAtMost(3)
            val dayExpected = if (isCurrent && day.date == input.today) expectedMealsSoFar(input) else 3
            eaten += count
            expected += maxOf(dayExpected, count)
        }
        if (expected == 0) return ScorePart("meals", "Three meals a day", MEALS_MAX, MEALS_MAX, "Day just started")
        val score = (MEALS_MAX * eaten.toFloat() / expected).roundToInt().coerceAtMost(MEALS_MAX)
        return ScorePart("meals", "Three meals a day", score, MEALS_MAX, "$eaten of $expected meals")
    }

    private fun expectedMealsSoFar(input: ScoreInput): Int {
        val s = input.settings
        var count = 0
        if (input.nowHour >= s.afternoonStart) count++
        if (input.nowHour >= s.eveningStart) count++
        if (input.nowHour >= s.nightStart + 1) count++
        return count
    }

    private fun monthPart(month: Long, lastMonth: Long): ScorePart {
        val title = "Saving vs last month"
        if (lastMonth == 0L) {
            return ScorePart("month", title, if (month == 0L) MONTH_MAX else 10, MONTH_MAX, "No data for last month yet")
        }
        val ratio = month.toFloat() / lastMonth
        val diff = month - lastMonth
        val detail = if (diff <= 0) "${Money.format(-diff)} saved so far" else "${Money.format(diff)} more so far"
        return ScorePart("month", title, ratioScore(MONTH_MAX, ratio, 0.9f, 1.2f), MONTH_MAX, detail)
    }

    fun grade(total: Int): String = when {
        total >= 90 -> "A+"
        total >= 80 -> "A"
        total >= 65 -> "B"
        total >= 50 -> "C"
        else -> "D"
    }

    private fun headline(total: Int, hasData: Boolean): String = when {
        !hasData -> "Start paying with Kaasu to see your marks"
        total >= 90 -> "Excellent week, keep it up!"
        total >= 80 -> "Great control this week"
        total >= 65 -> "Good, with a little room to improve"
        total >= 50 -> "Okay week, a few things to fix"
        else -> "Tough week, let's turn it around"
    }

    private fun tips(
        parts: List<ScorePart>,
        days: List<DayStat>,
        spend: Long,
        impulse: Long,
        lent: Long,
        lastWeek: Long,
        month: Long,
        lastMonth: Long,
        outstanding: Long,
        categories: List<CategoryStat>,
    ): List<String> {
        val byKey = parts.associateBy { it.key }
        val tips = ArrayList<String>()
        fun weak(key: String) = (byKey[key]?.fraction ?: 1f) < 0.7f

        val worst = days.maxByOrNull { it.spentPaise - it.budgetPaise }
        if (worst != null && worst.spentPaise > worst.budgetPaise) {
            tips += "Biggest overspend was ${Dates.weekdayShort(worst.date)}: ${Money.format(worst.spentPaise)} against a ${Money.format(worst.budgetPaise)} budget. Plan that day ahead next week."
        }
        if (weak("impulse") && impulse > 0) {
            val perDay = (impulse / 7 / 100 * 100).coerceAtLeast(1000)
            tips += "Snacks & treats took ${Money.format(impulse)} (${(impulse * 100 / spend.coerceAtLeast(1))}%). Cutting ${Money.format(perDay / 2)} a day saves ${Money.format(perDay / 2 * 7)} a week."
        }
        if (weak("week") && lastWeek > 0 && spend > lastWeek) {
            val top = categories.firstOrNull()
            val topText = top?.let { " Most of it went on ${it.category?.name ?: "untagged payments"}." } ?: ""
            tips += "You spent ${Money.format(spend - lastWeek)} more than the same days last week.$topText"
        }
        if (weak("lent") && lent > 0) {
            tips += "You lent ${Money.format(lent)} this week. Friends owe you ${Money.format(outstanding)} in total. Send a reminder from People."
        }
        if (weak("meals")) {
            tips += "Some meals are missing. Eat on time, and tap the meal chips on Home when you eat at home so they count."
        }
        if (weak("month") && lastMonth > 0 && month > lastMonth) {
            tips += "This month you've spent ${Money.format(month)}, ${Money.format(month - lastMonth)} more than by this date last month."
        }
        if (tips.isEmpty() && spend > 0) {
            tips += "Every area looks healthy. Try setting today's budget a little lower to save more."
        }
        return tips.take(4)
    }
}
