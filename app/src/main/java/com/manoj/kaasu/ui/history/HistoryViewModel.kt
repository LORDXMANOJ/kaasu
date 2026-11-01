package com.manoj.kaasu.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.ContactRepository
import com.manoj.kaasu.data.repo.PaymentRepository
import com.manoj.kaasu.ui.components.TxnUi
import com.manoj.kaasu.ui.components.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import javax.inject.Inject

enum class HistoryFilter(val label: String) {
    ALL("All"),
    PAID("Paid"),
    RECEIVED("Received"),
    UNTAGGED("Untagged"),
    UNCONFIRMED("Not confirmed"),
    LENT("Lent & got back"),
    CASH("Cash"),
}

@Immutable
data class DayGroup(
    val date: LocalDate,
    val label: String,
    val outPaise: Long,
    val inPaise: Long,
    val items: List<TxnUi>,
)

@Immutable
data class HistoryState(
    val loading: Boolean = true,
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val groups: List<DayGroup> = emptyList(),
    val monthLabel: String = "",
    val monthOutPaise: Long = 0,
    val monthInPaise: Long = 0,
    val totalCount: Int = 0,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    payments: PaymentRepository,
    categories: CategoryRepository,
    contacts: ContactRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val visibleQuery = MutableStateFlow("")

    val state: StateFlow<HistoryState> = combine(
        payments.all,
        categories.categories,
        contacts.all,
        query.debounce(150),
        filter,
    ) { list, cats, people, q, f ->
        val catMap = cats.associateBy { it.id }
        val peopleMap = people.associateBy { it.id }
        val needle = q.trim().lowercase()
        val filtered = list.filter { matches(it, f, catMap) }
            .map { it.toUi(catMap, peopleMap) }
            .filter { needle.isEmpty() || it.searchText.contains(needle) }
        val groups = filtered.groupBy { Dates.toLocalDate(it.createdAt) }
            .map { (date, items) ->
                DayGroup(
                    date = date,
                    label = Dates.dayHeader(date),
                    outPaise = items.filter { it.direction == Direction.OUT && it.status != PaymentStatus.FAILED }.sumOf { it.amountPaise },
                    inPaise = items.filter { it.direction == Direction.IN && it.status != PaymentStatus.FAILED }.sumOf { it.amountPaise },
                    items = items,
                )
            }
        val monthStart = Dates.monthStart(Dates.today())
        val thisMonth = list.filter { !Dates.toLocalDate(it.createdAt).isBefore(monthStart) && it.status != PaymentStatus.FAILED }
        HistoryState(
            loading = false,
            query = visibleQuery.value,
            filter = f,
            groups = groups,
            monthLabel = Dates.month(monthStart),
            monthOutPaise = thisMonth.filter { it.direction == Direction.OUT }.sumOf { it.amountPaise },
            monthInPaise = thisMonth.filter { it.direction == Direction.IN }.sumOf { it.amountPaise },
            totalCount = filtered.size,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    /** The text field reads this directly so typing never lags behind the debounced list. */
    val queryText: StateFlow<String> = visibleQuery

    fun onQueryChange(value: String) {
        visibleQuery.value = value
        query.update { value }
    }

    fun onFilterChange(value: HistoryFilter) {
        filter.value = value
    }

    private fun matches(p: Payment, f: HistoryFilter, cats: Map<Long, Category>): Boolean {
        val kind = cats[p.categoryId]?.kind
        return when (f) {
            HistoryFilter.ALL -> true
            HistoryFilter.PAID -> p.direction == Direction.OUT
            HistoryFilter.RECEIVED -> p.direction == Direction.IN
            HistoryFilter.UNTAGGED -> p.direction == Direction.OUT && p.categoryId == null && p.status != PaymentStatus.FAILED
            HistoryFilter.UNCONFIRMED -> p.status == PaymentStatus.PENDING || p.status == PaymentStatus.UNCONFIRMED
            HistoryFilter.LENT -> kind == CategoryKind.LENT || kind == CategoryKind.REPAYMENT
            HistoryFilter.CASH -> p.isCash
        }
    }
}
