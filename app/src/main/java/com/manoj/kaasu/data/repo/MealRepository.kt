package com.manoj.kaasu.data.repo

import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.data.db.MealLog
import com.manoj.kaasu.data.db.MealLogDao
import com.manoj.kaasu.data.db.MealType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepository @Inject constructor(private val dao: MealLogDao) {

    fun observeRange(from: LocalDate, toInclusive: LocalDate): Flow<List<MealLog>> =
        dao.observeBetween(from.toEpochDay(), toInclusive.toEpochDay())

    suspend fun getRange(from: LocalDate, toInclusive: LocalDate): List<MealLog> =
        dao.getBetween(from.toEpochDay(), toInclusive.toEpochDay())

    suspend fun setEatenAtHome(day: LocalDate, meal: MealType, eaten: Boolean) {
        if (eaten) {
            dao.insert(MealLog(epochDay = day.toEpochDay(), mealType = meal, createdAt = Dates.now()))
        } else {
            dao.delete(day.toEpochDay(), meal)
        }
    }
}
