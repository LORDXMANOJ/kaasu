package com.manoj.kaasu.data.repo

import com.manoj.kaasu.core.TimeSlot
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryDao
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.MealType
import com.manoj.kaasu.data.db.PaymentDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val paymentDao: PaymentDao,
) {
    private val seedMutex = Mutex()

    val categories: Flow<List<Category>> = categoryDao.observeAll()

    suspend fun getAll(): List<Category> = categoryDao.getAll()

    suspend fun firstOfKind(kind: CategoryKind): Category? = categoryDao.firstOfKind(kind)

    suspend fun ensureSeeded() = seedMutex.withLock {
        if (categoryDao.count() == 0) categoryDao.insertAll(defaultCategories())
    }

    suspend fun save(category: Category): Long {
        if (category.id == 0L) {
            val nextOrder = (categoryDao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
            return categoryDao.insert(category.copy(sortOrder = nextOrder))
        }
        categoryDao.update(category)
        return category.id
    }

    suspend fun move(category: Category, up: Boolean) {
        val visible = categoryDao.getAll().filter { it.kind != CategoryKind.REPAYMENT && it.kind != CategoryKind.INCOME }
        val index = visible.indexOfFirst { it.id == category.id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in visible.indices) return
        val reordered = visible.toMutableList().apply { add(target, removeAt(index)) }
        categoryDao.updateAll(reordered.mapIndexed { i, c -> c.copy(sortOrder = i) })
    }

    suspend fun archive(category: Category, archived: Boolean) = categoryDao.update(category.copy(archived = archived))

    suspend fun detachPayments(categoryId: Long) = paymentDao.clearCategory(categoryId)

    companion object {
        private val M = TimeSlot.MORNING.bit
        private val A = TimeSlot.AFTERNOON.bit
        private val E = TimeSlot.EVENING.bit
        private val N = TimeSlot.NIGHT.bit

        fun defaultCategories(): List<Category> = listOf(
            Category(name = "Breakfast", icon = "breakfast", colorIndex = 0, kind = CategoryKind.MEAL, mealType = MealType.BREAKFAST, pinnedSlots = M, sortOrder = 0),
            Category(name = "Petrol", icon = "fuel", colorIndex = 1, kind = CategoryKind.NORMAL, pinnedSlots = M, sortOrder = 1),
            Category(name = "Snacks", icon = "snacks", colorIndex = 2, kind = CategoryKind.NORMAL, isImpulse = true, pinnedSlots = E, sortOrder = 2),
            Category(name = "Tea & Coffee", icon = "coffee", colorIndex = 3, kind = CategoryKind.NORMAL, isImpulse = true, pinnedSlots = M or E, sortOrder = 3),
            Category(name = "Lunch", icon = "lunch", colorIndex = 4, kind = CategoryKind.MEAL, mealType = MealType.LUNCH, pinnedSlots = A, sortOrder = 4),
            Category(name = "Dinner", icon = "dinner", colorIndex = 5, kind = CategoryKind.MEAL, mealType = MealType.DINNER, pinnedSlots = E or N, sortOrder = 5),
            Category(name = "Travel", icon = "bus", colorIndex = 6, kind = CategoryKind.NORMAL, sortOrder = 6),
            Category(name = "Groceries", icon = "cart", colorIndex = 7, kind = CategoryKind.NORMAL, sortOrder = 7),
            Category(name = "Shopping", icon = "bag", colorIndex = 8, kind = CategoryKind.NORMAL, isImpulse = true, sortOrder = 8),
            Category(name = "Recharge & Bills", icon = "bolt", colorIndex = 9, kind = CategoryKind.NORMAL, sortOrder = 9),
            Category(name = "Health", icon = "health", colorIndex = 10, kind = CategoryKind.NORMAL, sortOrder = 10),
            Category(name = "Fun & Movies", icon = "movie", colorIndex = 11, kind = CategoryKind.NORMAL, isImpulse = true, sortOrder = 11),
            Category(name = "Lent to friend", icon = "handshake", colorIndex = 12, kind = CategoryKind.LENT, sortOrder = 12),
            Category(name = "Other", icon = "more", colorIndex = 13, kind = CategoryKind.OTHER, sortOrder = 99),
            Category(name = "Got back", icon = "repay", colorIndex = 14, kind = CategoryKind.REPAYMENT, sortOrder = 100),
            Category(name = "Received", icon = "income", colorIndex = 15, kind = CategoryKind.INCOME, sortOrder = 101),
        )
    }
}
