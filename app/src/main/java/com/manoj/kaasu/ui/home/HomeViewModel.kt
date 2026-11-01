package com.manoj.kaasu.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.TimeSlot
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.repo.ManualEntry
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.MealLog
import com.manoj.kaasu.data.db.MealType
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentSource
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.prefs.UserSettings
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.ContactRepository
import com.manoj.kaasu.data.repo.MealRepository
import com.manoj.kaasu.data.repo.PaymentRepository
import com.manoj.kaasu.data.repo.SmsRepository
import com.manoj.kaasu.domain.BalanceCalculator
import com.manoj.kaasu.domain.BalanceInfo
import com.manoj.kaasu.domain.BudgetRules
import com.manoj.kaasu.domain.DebtCalculator
import com.manoj.kaasu.domain.ScoreInput
import com.manoj.kaasu.domain.WeeklyScoreCalculator
import com.manoj.kaasu.ui.components.TxnUi
import com.manoj.kaasu.ui.components.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class MealMark { NONE, HOME, PAID }

@Immutable
data class HomeState(
    val loading: Boolean = true,
    val userName: String = "",
    val greeting: String = "",
    val todayLabel: String = "",
    val budgetPaise: Long = 0,
    val defaultBudgetPaise: Long = 0,
    val spentPaise: Long = 0,
    val hasTodayOverride: Boolean = false,
    val balance: BalanceInfo? = null,
    val smsEnabled: Boolean = false,
    val meals: Map<MealType, MealMark> = emptyMap(),
    val recent: List<TxnUi> = emptyList(),
    val untaggedCount: Int = 0,
    val lastFailed: TxnUi? = null,
    val weekScore: Int = 0,
    val weekGrade: String = "",
    val weekHasData: Boolean = false,
    val owedToYouPaise: Long = 0,
    val peopleOwing: Int = 0,
    val hasAnyPayment: Boolean = false,
    val defaultUpiPackage: String? = null,
    val homeStyle: com.manoj.kaasu.ui.home.styles.HomeStyle = com.manoj.kaasu.ui.home.styles.HomeStyle.CLASSIC,
) {
    val leftPaise: Long get() = budgetPaise - spentPaise
    val progress: Float get() = if (budgetPaise <= 0) 0f else spentPaise.toFloat() / budgetPaise
}

sealed interface HomeEvent {
    data class ToggleMeal(val meal: MealType) : HomeEvent
    data class SetTodayBudget(val paise: Long?) : HomeEvent
    data class SetBalance(val paise: Long) : HomeEvent
    data class AddMoneyIn(val paise: Long) : HomeEvent
    data class SetHomeStyle(val style: com.manoj.kaasu.ui.home.styles.HomeStyle) : HomeEvent
}

private data class Base(
    val settings: UserSettings,
    val payments: List<Payment>,
    val categories: Map<Long, Category>,
    val contacts: Map<Long, Contact>,
    val today: LocalDate,
)

private data class Extra(val meals: List<MealLog>, val balanceSms: SmsEvent?, val untagged: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val payments: PaymentRepository,
    private val categories: CategoryRepository,
    contacts: ContactRepository,
    private val meals: MealRepository,
    sms: SmsRepository,
) : ViewModel() {

    /** Re-emits when the date changes so the budget resets at midnight even if the app stays open. */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(Dates.today())
            delay(30_000)
        }
    }.distinctUntilChanged()

    private val base: Flow<Base> = combine(
        settings.settings,
        payments.all,
        categories.categories,
        contacts.all,
        today,
    ) { s, p, c, ct, day -> Base(s, p, c.associateBy { it.id }, ct.associateBy { it.id }, day) }

    private val extra: Flow<Extra> = combine(
        today.flatMapLatest { day -> meals.observeRange(day.minusDays(MEAL_WINDOW_DAYS), day) },
        sms.latestWithBalance,
        settings.settings.flatMapLatest { s -> payments.observeUntagged(s.untaggedSince) },
    ) { m, b, u -> Extra(m, b, u.size) }

    val state: StateFlow<HomeState> = combine(base, extra) { b, e -> build(b, e) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun build(b: Base, e: Extra): HomeState {
        val s = b.settings
        val day = b.today
        val todayPayments = b.payments.filter { Dates.toLocalDate(it.createdAt) == day }
        val spent = BudgetRules.spentOn(day, todayPayments, b.categories, s.countLentInBudget)
        val mealsToday = WeeklyScoreCalculator.mealsByDay(todayPayments, emptyList(), b.categories)[day].orEmpty()
        val homeMeals = e.meals.filter { it.epochDay == day.toEpochDay() }.map { it.mealType }.toSet()
        val mealMarks = MealType.entries.associateWith { meal ->
            when (meal) {
                in mealsToday -> MealMark.PAID
                in homeMeals -> MealMark.HOME
                else -> MealMark.NONE
            }
        }
        val debts = DebtCalculator.byContact(b.payments, b.categories).filterValues { it.outstandingPaise > 0 }
        val weekStart = Dates.weekStart(day)
        val report = WeeklyScoreCalculator.compute(
            ScoreInput(
                weekStart = weekStart,
                today = day,
                nowHour = LocalTime.now().hour,
                payments = b.payments.filter { !Dates.toLocalDate(it.createdAt).isBefore(Dates.monthStart(day).minusMonths(1).coerceAtMost(weekStart.minusDays(7))) },
                meals = e.meals,
                categories = b.categories,
                settings = s,
                outstandingLentPaise = debts.values.sumOf { it.outstandingPaise },
            ),
        )
        val now = Dates.now()
        val lastFailed = b.payments.firstOrNull {
            it.source == PaymentSource.APP && it.status == PaymentStatus.FAILED && now - it.createdAt < FAILED_BANNER_MS
        }
        return HomeState(
            loading = false,
            userName = s.userName,
            greeting = greeting(s),
            todayLabel = Dates.dayHeader(day).let { if (it == "Today") Dates.dayMonth(day) else it },
            budgetPaise = s.budgetForDay(day.toEpochDay()),
            defaultBudgetPaise = s.dailyBudgetPaise,
            spentPaise = spent,
            hasTodayOverride = s.todayOverrideDay == day.toEpochDay() && s.todayOverridePaise != null,
            balance = BalanceCalculator.compute(e.balanceSms, s.openingBalancePaise, s.openingBalanceAt, b.payments),
            smsEnabled = s.smsSyncEnabled,
            meals = mealMarks,
            recent = b.payments.take(RECENT_COUNT).map { it.toUi(b.categories, b.contacts) },
            untaggedCount = e.untagged,
            lastFailed = lastFailed?.toUi(b.categories, b.contacts),
            weekScore = report.total,
            weekGrade = report.grade,
            weekHasData = report.hasData,
            owedToYouPaise = debts.values.sumOf { it.outstandingPaise },
            peopleOwing = debts.size,
            hasAnyPayment = b.payments.any { it.direction == Direction.OUT },
            defaultUpiPackage = s.defaultUpiPackage,
            homeStyle = com.manoj.kaasu.ui.home.styles.HomeStyle.from(s.homeStyle),
        )
    }

    private fun greeting(s: UserSettings): String = when (s.slots.slotFor(LocalTime.now().hour)) {
        TimeSlot.MORNING -> "Good morning"
        TimeSlot.AFTERNOON -> "Good afternoon"
        TimeSlot.EVENING -> "Good evening"
        TimeSlot.NIGHT -> "Good night"
    }

    fun onEvent(event: HomeEvent) {
        viewModelScope.launch {
            when (event) {
                is HomeEvent.ToggleMeal -> {
                    val current = state.value.meals[event.meal] ?: MealMark.NONE
                    if (current != MealMark.PAID) meals.setEatenAtHome(Dates.today(), event.meal, current == MealMark.NONE)
                }
                is HomeEvent.SetTodayBudget -> settings.setTodayOverride(Dates.today().toEpochDay(), event.paise)
                is HomeEvent.SetBalance -> settings.setOpeningBalance(event.paise)
                is HomeEvent.SetHomeStyle -> settings.setHomeStyle(event.style.name)
                is HomeEvent.AddMoneyIn -> payments.addManual(
                    ManualEntry(
                        direction = Direction.IN,
                        amountPaise = event.paise,
                        categoryId = categories.firstOfKind(CategoryKind.INCOME)?.id,
                        contactId = null,
                        note = "Added by hand",
                    ),
                    isCash = false,
                )
            }
        }
    }

    companion object {
        private const val RECENT_COUNT = 6
        private const val MEAL_WINDOW_DAYS = 70L
        private const val FAILED_BANNER_MS = 30 * 60_000L
    }
}

private fun LocalDate.coerceAtMost(other: LocalDate): LocalDate = if (isAfter(other)) other else this
