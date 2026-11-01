package com.manoj.kaasu.domain

import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.TimeSlot
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentSource
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.prefs.UserSettings
import com.manoj.kaasu.data.repo.CategoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainRulesTest {
    private val categories = CategoryRepository.defaultCategories().mapIndexed { i, c -> c.copy(id = i + 1L) }
    private val byName = categories.associateBy { it.name }
    private val catMap = categories.associateBy { it.id }
    private val settings = UserSettings(onboardingDone = true, dailyBudgetPaise = 20_000)
    private var nextId = 1L

    private fun pay(
        date: LocalDate,
        hour: Int,
        amount: Long,
        category: String?,
        direction: Direction = Direction.OUT,
        status: PaymentStatus = PaymentStatus.SUCCESS,
        smsEventId: Long? = null,
    ): Payment {
        val at = Dates.startOfDay(date) + hour * 3_600_000L
        return Payment(
            id = nextId++,
            direction = direction,
            amountPaise = amount,
            categoryId = category?.let { byName.getValue(it).id },
            status = status,
            source = PaymentSource.APP,
            txnRef = "T$nextId",
            createdAt = at,
            updatedAt = at,
            smsEventId = smsEventId,
        )
    }

    @Test
    fun morningShowsBreakfastAndPetrolFirst() {
        val ordered = CategoryOrdering.forPayment(categories, TimeSlot.MORNING, toPerson = false)
        assertEquals(listOf("Breakfast", "Petrol", "Tea & Coffee"), ordered.suggested.map { it.name })
        assertEquals("Other", ordered.others.last().name)
        assertFalse(ordered.all.any { it.kind == CategoryKind.REPAYMENT || it.kind == CategoryKind.INCOME })
    }

    @Test
    fun eveningShowsSnacksFirst() {
        val ordered = CategoryOrdering.forPayment(categories, TimeSlot.EVENING, toPerson = false)
        assertEquals("Snacks", ordered.suggested.first().name)
        assertTrue(ordered.suggested.map { it.name }.containsAll(listOf("Tea & Coffee", "Dinner")))
    }

    @Test
    fun payingAPersonPutsLendingFirst() {
        val ordered = CategoryOrdering.forPayment(categories, TimeSlot.AFTERNOON, toPerson = true)
        assertEquals("Lent to friend", ordered.suggested.first().name)
        assertEquals("Lunch", ordered.suggested[1].name)
    }

    @Test
    fun archivedCategoriesAreHidden() {
        val withArchived = categories.map { if (it.name == "Petrol") it.copy(archived = true) else it }
        val ordered = CategoryOrdering.forPayment(withArchived, TimeSlot.MORNING, toPerson = false)
        assertFalse(ordered.all.any { it.name == "Petrol" })
    }

    @Test
    fun slotBoundaries() {
        val slots = UserSettings().slots
        assertEquals(TimeSlot.NIGHT, slots.slotFor(4))
        assertEquals(TimeSlot.MORNING, slots.slotFor(5))
        assertEquals(TimeSlot.MORNING, slots.slotFor(10))
        assertEquals(TimeSlot.AFTERNOON, slots.slotFor(11))
        assertEquals(TimeSlot.EVENING, slots.slotFor(16))
        assertEquals(TimeSlot.NIGHT, slots.slotFor(21))
        assertEquals(TimeSlot.NIGHT, slots.slotFor(0))
    }

    @Test
    fun budgetWarnsWhenGoingOver() {
        val today = LocalDate.of(2026, 11, 1)
        val spent = listOf(pay(today, 9, 15_000, "Breakfast"))
        val check = BudgetRules.check(8_000, byName["Snacks"], spent, catMap, settings, today)
        assertEquals(15_000L, check.spentPaise)
        assertEquals(3_000L, check.overByPaise)
        assertTrue(check.hasWarning)
    }

    @Test
    fun lendingIsNotCountedUnlessAskedTo() {
        val today = LocalDate.of(2026, 11, 1)
        val spent = listOf(pay(today, 9, 15_000, "Breakfast"), pay(today, 10, 50_000, "Lent to friend"))
        val check = BudgetRules.check(50_000, byName["Lent to friend"], spent, catMap, settings, today)
        assertEquals(15_000L, check.spentPaise)
        assertFalse(check.countsTowardBudget)
        assertEquals(0L, check.overByPaise)

        val counting = BudgetRules.check(1_000, byName["Snacks"], spent, catMap, settings.copy(countLentInBudget = true), today)
        assertEquals(65_000L, counting.spentPaise)
    }

    @Test
    fun failedPaymentsDoNotCount() {
        val today = LocalDate.of(2026, 11, 1)
        val spent = listOf(pay(today, 9, 15_000, "Breakfast", status = PaymentStatus.FAILED))
        assertEquals(0L, BudgetRules.spentOn(today, spent, catMap, false))
    }

    @Test
    fun todayOverrideBudget() {
        val day = LocalDate.of(2026, 11, 1).toEpochDay()
        val s = settings.copy(todayOverrideDay = day, todayOverridePaise = 50_000)
        assertEquals(50_000L, s.budgetForDay(day))
        assertEquals(20_000L, s.budgetForDay(day + 1))
    }

    @Test
    fun balanceFromSmsAdjustsForLaterPayments() {
        val day = LocalDate.of(2026, 11, 1)
        val smsAt = Dates.startOfDay(day) + 8 * 3_600_000L
        val sms = SmsEvent(id = 7, providerId = 1, sender = "VM-BANK", body = "", receivedAt = smsAt, direction = Direction.OUT, amountPaise = 1_000, balancePaise = 500_000)
        val linked = pay(day, 7, 1_000, "Breakfast", smsEventId = 7).copy(createdAt = smsAt + 1)
        val later = pay(day, 12, 10_000, "Lunch")
        val incoming = pay(day, 13, 2_500, null, direction = Direction.IN)
        val pending = pay(day, 14, 5_000, "Snacks", status = PaymentStatus.PENDING)
        val info = BalanceCalculator.compute(sms, null, null, listOf(linked, later, incoming, pending))!!
        assertEquals(500_000L - 10_000 + 2_500, info.amountPaise)
        assertTrue(info.isEstimate)
        assertTrue(info.fromSms)
    }

    @Test
    fun manualBalanceWinsWhenNewer() {
        val day = LocalDate.of(2026, 11, 1)
        val sms = SmsEvent(id = 1, providerId = 1, sender = "B", body = "", receivedAt = Dates.startOfDay(day), direction = Direction.OUT, amountPaise = 1, balancePaise = 100)
        val info = BalanceCalculator.compute(sms, 900_000, Dates.startOfDay(day) + 3_600_000, emptyList())!!
        assertEquals(900_000L, info.amountPaise)
        assertFalse(info.fromSms)
    }

    @Test
    fun debtsAreTrackedPerPerson() {
        val day = LocalDate.of(2026, 11, 1)
        val lent = pay(day, 10, 50_000, "Lent to friend").copy(contactId = 3)
        val back = pay(day, 18, 20_000, "Got back", direction = Direction.IN).copy(contactId = 3)
        val other = pay(day, 11, 9_000, "Lunch").copy(contactId = 3)
        val summary = DebtCalculator.byContact(listOf(lent, back, other), catMap).getValue(3)
        assertEquals(50_000L, summary.lentPaise)
        assertEquals(20_000L, summary.returnedPaise)
        assertEquals(30_000L, summary.outstandingPaise)
    }

    @Test
    fun perfectWeekScoresHigh() {
        val weekStart = LocalDate.of(2026, 10, 26)
        val payments = (0 until 7).flatMap { d ->
            val date = weekStart.plusDays(d.toLong())
            listOf(pay(date, 8, 5_000, "Breakfast"), pay(date, 13, 8_000, "Lunch"), pay(date, 20, 6_000, "Dinner"))
        }
        val report = WeeklyScoreCalculator.compute(
            ScoreInput(weekStart, weekStart.plusDays(6), nowHour = 23, payments = payments, meals = emptyList(), categories = catMap, settings = settings, outstandingLentPaise = 0),
        )
        val parts = report.parts.associateBy { it.key }
        assertEquals(25, parts.getValue("budget").score)
        assertEquals(15, parts.getValue("impulse").score)
        assertEquals(20, parts.getValue("meals").score)
        assertEquals(10, parts.getValue("lent").score)
        assertEquals(90, report.total)
        assertEquals("A+", report.grade)
        assertEquals(7 * 19_000L, report.spendPaise)
    }

    @Test
    fun overspendingAndSnacksLowerTheScore() {
        val weekStart = LocalDate.of(2026, 10, 26)
        val payments = (0 until 7).flatMap { d ->
            val date = weekStart.plusDays(d.toLong())
            listOf(pay(date, 8, 5_000, "Breakfast"), pay(date, 17, if (d == 3) 40_000 else 9_000, "Snacks"))
        }
        val report = WeeklyScoreCalculator.compute(
            ScoreInput(weekStart, weekStart.plusDays(6), nowHour = 23, payments = payments, meals = emptyList(), categories = catMap, settings = settings, outstandingLentPaise = 0),
        )
        val parts = report.parts.associateBy { it.key }
        assertEquals("6 of 7 days", parts.getValue("budget").detail)
        assertEquals(0, parts.getValue("impulse").score)
        assertTrue(parts.getValue("meals").score < 10)
        assertTrue(report.total < 60)
        assertTrue(report.tips.any { it.contains("overspend", ignoreCase = true) })
        assertTrue(report.tips.any { it.contains("Snacks", ignoreCase = true) })
    }

    @Test
    fun mealsLoggedAtHomeCount() {
        val weekStart = LocalDate.of(2026, 10, 26)
        val logs = (0 until 7).flatMap { d ->
            com.manoj.kaasu.data.db.MealType.entries.map { m ->
                com.manoj.kaasu.data.db.MealLog(epochDay = weekStart.plusDays(d.toLong()).toEpochDay(), mealType = m, createdAt = 0)
            }
        }
        val report = WeeklyScoreCalculator.compute(
            ScoreInput(weekStart, weekStart.plusDays(6), nowHour = 23, payments = emptyList(), meals = logs, categories = catMap, settings = settings, outstandingLentPaise = 0),
        )
        assertEquals(20, report.parts.first { it.key == "meals" }.score)
        assertTrue(report.hasData)
    }

    @Test
    fun midWeekOnlyCountsDaysSoFar() {
        val weekStart = LocalDate.of(2026, 10, 26)
        val today = weekStart.plusDays(2)
        val payments = listOf(pay(weekStart, 8, 5_000, "Breakfast"))
        val report = WeeklyScoreCalculator.compute(
            ScoreInput(weekStart, today, nowHour = 9, payments = payments, meals = emptyList(), categories = catMap, settings = settings, outstandingLentPaise = 0),
        )
        assertTrue(report.isCurrentWeek)
        assertEquals(3, report.daysElapsed)
        assertEquals("3 of 3 days", report.parts.first { it.key == "budget" }.detail)
        assertTrue(report.days.drop(3).all { it.isFuture })
    }

    @Test
    fun daysBeforeStartingKaasuAreNotCounted() {
        val weekStart = LocalDate.of(2026, 10, 26)
        val sunday = weekStart.plusDays(6)
        val started = settings.copy(untaggedSince = Dates.startOfDay(sunday) + 17 * 3_600_000L)
        val payments = listOf(pay(sunday, 18, 4_000, "Snacks"))
        val report = WeeklyScoreCalculator.compute(
            ScoreInput(weekStart, sunday, nowHour = 18, payments = payments, meals = emptyList(), categories = catMap, settings = started, outstandingLentPaise = 0),
        )
        val parts = report.parts.associateBy { it.key }
        assertEquals("1 of 1 days", parts.getValue("budget").detail)
        assertEquals("0 of 2 meals", parts.getValue("meals").detail)
    }

    @Test
    fun grades() {
        assertEquals("A+", WeeklyScoreCalculator.grade(95))
        assertEquals("A", WeeklyScoreCalculator.grade(80))
        assertEquals("B", WeeklyScoreCalculator.grade(70))
        assertEquals("C", WeeklyScoreCalculator.grade(50))
        assertEquals("D", WeeklyScoreCalculator.grade(10))
    }

    @Suppress("unused")
    private fun Category.isPinnedIn(slot: TimeSlot) = pinnedSlots and slot.bit != 0
}
