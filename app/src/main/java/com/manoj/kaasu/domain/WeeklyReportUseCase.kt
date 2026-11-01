package com.manoj.kaasu.domain

import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.prefs.UserSettings
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.MealRepository
import com.manoj.kaasu.data.repo.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ReportBundle(val report: WeeklyReport, val settings: UserSettings)

/** Builds the weekly report card for any week, live-updating as payments change. */
class WeeklyReportUseCase @Inject constructor(
    private val payments: PaymentRepository,
    private val categories: CategoryRepository,
    private val meals: MealRepository,
    private val settings: SettingsRepository,
) {
    operator fun invoke(weekStart: LocalDate): Flow<ReportBundle> {
        val weekEnd = weekStart.plusDays(6)
        val from = minOf(Dates.monthStart(weekEnd).minusMonths(1), weekStart.minusDays(7))
        return combine(
            payments.all,
            categories.categories,
            meals.observeRange(weekStart, weekEnd),
            settings.settings,
        ) { all, cats, mealLogs, s ->
            val catMap = cats.associateBy { it.id }
            val outstanding = DebtCalculator.byContact(all, catMap).values.sumOf { it.outstandingPaise.coerceAtLeast(0) }
            val relevant = all.filter { !Dates.toLocalDate(it.createdAt).isBefore(from) }
            val report = WeeklyScoreCalculator.compute(
                ScoreInput(
                    weekStart = weekStart,
                    today = Dates.today(),
                    nowHour = LocalTime.now().hour,
                    payments = relevant,
                    meals = mealLogs,
                    categories = catMap,
                    settings = s,
                    outstandingLentPaise = outstanding,
                ),
            )
            ReportBundle(report, s)
        }
    }
}
