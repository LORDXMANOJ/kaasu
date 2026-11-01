package com.manoj.kaasu.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Payment::class, Category::class, Contact::class, MealLog::class, SmsEvent::class],
    version = 1,
    exportSchema = true,
)
abstract class KaasuDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun contactDao(): ContactDao
    abstract fun mealLogDao(): MealLogDao
    abstract fun smsEventDao(): SmsEventDao

    companion object {
        const val NAME = "kaasu.db"
    }
}
