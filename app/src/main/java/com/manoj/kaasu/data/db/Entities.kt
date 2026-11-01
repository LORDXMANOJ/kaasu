package com.manoj.kaasu.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class Direction { OUT, IN }

enum class PaymentStatus { PENDING, SUCCESS, FAILED, UNCONFIRMED }

enum class PaymentSource { APP, SMS, MANUAL }

enum class CategoryKind { NORMAL, MEAL, LENT, REPAYMENT, INCOME, OTHER }

enum class MealType(val label: String) { BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner") }

@Serializable
@Entity(
    tableName = "payments",
    indices = [
        Index("createdAt"),
        Index("categoryId"),
        Index("contactId"),
        Index(value = ["txnRef"], unique = true),
    ],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: Direction,
    val amountPaise: Long,
    val payeeVpa: String? = null,
    val payeeName: String? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val contactId: Long? = null,
    val status: PaymentStatus,
    val source: PaymentSource,
    val upiApp: String? = null,
    /** Our own unique reference for this record. */
    val txnRef: String,
    val upiTxnId: String? = null,
    /** Bank reference / UTR / RRN from the UPI app or bank SMS. */
    val bankRef: String? = null,
    val responseCode: String? = null,
    val rawResponse: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val dueAt: Long? = null,
    val smsEventId: Long? = null,
    val isCash: Boolean = false,
)

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val colorIndex: Int,
    val kind: CategoryKind,
    val mealType: MealType? = null,
    val isImpulse: Boolean = false,
    /** Bitmask of TimeSlot.bit values where this category is shown on top. */
    val pinnedSlots: Int = 0,
    val sortOrder: Int,
    val archived: Boolean = false,
    val dailyLimitPaise: Long? = null,
)

@Serializable
@Entity(tableName = "contacts", indices = [Index("vpa")])
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val vpa: String? = null,
    val phone: String? = null,
    val createdAt: Long,
    val archived: Boolean = false,
)

/** Meals eaten without a payment (e.g. at home), so the meal score stays honest. */
@Serializable
@Entity(tableName = "meal_logs", indices = [Index(value = ["epochDay", "mealType"], unique = true)])
data class MealLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val mealType: MealType,
    val createdAt: Long,
)

/** A bank transaction SMS that was parsed. Only transaction SMS are stored, nothing else. */
@Serializable
@Entity(tableName = "sms_events", indices = [Index(value = ["providerId"], unique = true), Index("receivedAt")])
data class SmsEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val direction: Direction,
    val amountPaise: Long,
    val balancePaise: Long? = null,
    val refNo: String? = null,
    val counterparty: String? = null,
    val account: String? = null,
    val paymentId: Long? = null,
)
