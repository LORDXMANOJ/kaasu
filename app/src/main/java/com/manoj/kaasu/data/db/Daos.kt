package com.manoj.kaasu.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    suspend fun getBetween(from: Long, to: Long): List<Payment>

    @Query("SELECT * FROM payments WHERE id = :id")
    fun observeById(id: Long): Flow<Payment?>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Long): Payment?

    @Query("SELECT * FROM payments WHERE contactId = :contactId ORDER BY createdAt DESC")
    fun observeByContact(contactId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE contactId IS NOT NULL")
    fun observeWithContact(): Flow<List<Payment>>

    @Query(
        "SELECT * FROM payments WHERE direction = 'OUT' AND categoryId IS NULL AND status != 'FAILED' " +
            "AND createdAt >= :since ORDER BY createdAt DESC",
    )
    fun observeUntagged(since: Long): Flow<List<Payment>>

    @Query(
        "SELECT * FROM payments WHERE direction = :direction AND amountPaise = :amount " +
            "AND createdAt BETWEEN :from AND :to AND smsEventId IS NULL ORDER BY createdAt DESC",
    )
    suspend fun findUnlinked(direction: Direction, amount: Long, from: Long, to: Long): List<Payment>

    @Query("SELECT * FROM payments WHERE bankRef = :ref OR upiTxnId = :ref LIMIT 1")
    suspend fun findByRef(ref: String): Payment?

    @Query("SELECT * FROM payments WHERE status = 'PENDING' AND createdAt < :before")
    suspend fun getPendingOlderThan(before: Long): List<Payment>

    @Query("SELECT * FROM payments ORDER BY createdAt ASC")
    suspend fun getAll(): List<Payment>

    @Insert
    suspend fun insert(payment: Payment): Long

    @Insert
    suspend fun insertAll(payments: List<Payment>)

    @Update
    suspend fun update(payment: Payment)

    @Delete
    suspend fun delete(payment: Payment)

    @Query("UPDATE payments SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("UPDATE payments SET contactId = NULL WHERE contactId = :contactId")
    suspend fun clearContact(contactId: Long)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE kind = :kind AND archived = 0 ORDER BY sortOrder LIMIT 1")
    suspend fun firstOfKind(kind: CategoryKind): Category?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Update
    suspend fun updateAll(categories: List<Category>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts")
    fun observeAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observeById(id: Long): Flow<Contact?>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): Contact?

    @Query("SELECT * FROM contacts WHERE archived = 0")
    suspend fun getActive(): List<Contact>

    @Query("SELECT * FROM contacts WHERE lower(vpa) = lower(:vpa) LIMIT 1")
    suspend fun findByVpa(vpa: String): Contact?

    @Query("SELECT * FROM contacts")
    suspend fun getAll(): List<Contact>

    @Insert
    suspend fun insert(contact: Contact): Long

    @Insert
    suspend fun insertAll(contacts: List<Contact>)

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}

@Dao
interface MealLogDao {
    @Query("SELECT * FROM meal_logs WHERE epochDay BETWEEN :fromDay AND :toDay")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<MealLog>>

    @Query("SELECT * FROM meal_logs WHERE epochDay BETWEEN :fromDay AND :toDay")
    suspend fun getBetween(fromDay: Long, toDay: Long): List<MealLog>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: MealLog): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<MealLog>)

    @Query("DELETE FROM meal_logs WHERE epochDay = :day AND mealType = :meal")
    suspend fun delete(day: Long, meal: MealType)

    @Query("SELECT * FROM meal_logs")
    suspend fun getAll(): List<MealLog>

    @Query("DELETE FROM meal_logs")
    suspend fun deleteAll()
}

@Dao
interface SmsEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: SmsEvent): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<SmsEvent>)

    @Update
    suspend fun update(event: SmsEvent)

    @Query("SELECT * FROM sms_events WHERE balancePaise IS NOT NULL ORDER BY receivedAt DESC LIMIT 1")
    fun observeLatestWithBalance(): Flow<SmsEvent?>

    @Query("SELECT * FROM sms_events WHERE id = :id")
    suspend fun getById(id: Long): SmsEvent?

    @Query("SELECT * FROM sms_events")
    suspend fun getAll(): List<SmsEvent>

    @Query("SELECT COUNT(*) FROM sms_events")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM sms_events")
    suspend fun deleteAll()
}
