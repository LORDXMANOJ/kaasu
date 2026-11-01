package com.manoj.kaasu.data.repo

import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentDao
import com.manoj.kaasu.data.db.PaymentSource
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.upi.UpiResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NewAppPayment(
    val amountPaise: Long,
    val payeeVpa: String,
    val payeeName: String?,
    val categoryId: Long?,
    val note: String?,
    val contactId: Long?,
    val upiApp: String,
    val txnRef: String,
    val dueAt: Long?,
)

data class ManualEntry(
    val direction: Direction,
    val amountPaise: Long,
    val categoryId: Long?,
    val contactId: Long?,
    val note: String?,
    val at: Long = Dates.now(),
)

@Singleton
class PaymentRepository @Inject constructor(private val dao: PaymentDao) {

    val all: Flow<List<Payment>> = dao.observeAll()

    fun observeById(id: Long): Flow<Payment?> = dao.observeById(id)
    fun observeByContact(contactId: Long): Flow<List<Payment>> = dao.observeByContact(contactId)
    fun observeWithContact(): Flow<List<Payment>> = dao.observeWithContact()
    fun observeUntagged(since: Long): Flow<List<Payment>> = dao.observeUntagged(since)

    fun observeRange(from: LocalDate, toInclusive: LocalDate): Flow<List<Payment>> =
        dao.observeBetween(Dates.startOfDay(from), Dates.endOfDay(toInclusive))

    suspend fun getRange(from: LocalDate, toInclusive: LocalDate): List<Payment> =
        dao.getBetween(Dates.startOfDay(from), Dates.endOfDay(toInclusive))

    suspend fun getById(id: Long): Payment? = dao.getById(id)

    /** Saved BEFORE the UPI app opens, so nothing is lost if the phone kills our app meanwhile. */
    suspend fun createPending(p: NewAppPayment): Long {
        val now = Dates.now()
        return dao.insert(
            Payment(
                direction = Direction.OUT,
                amountPaise = p.amountPaise,
                payeeVpa = p.payeeVpa,
                payeeName = p.payeeName,
                categoryId = p.categoryId,
                note = p.note,
                contactId = p.contactId,
                status = PaymentStatus.PENDING,
                source = PaymentSource.APP,
                upiApp = p.upiApp,
                txnRef = p.txnRef,
                createdAt = now,
                updatedAt = now,
                dueAt = p.dueAt,
            ),
        )
    }

    suspend fun applyUpiResult(paymentId: Long, result: UpiResult) {
        val payment = dao.getById(paymentId) ?: return
        if (payment.status == PaymentStatus.SUCCESS && payment.smsEventId != null) return
        val status = result.status ?: payment.status
        dao.update(
            payment.copy(
                status = status,
                upiTxnId = result.txnId ?: payment.upiTxnId,
                bankRef = payment.bankRef ?: result.approvalRef,
                responseCode = result.responseCode ?: payment.responseCode,
                rawResponse = result.raw ?: payment.rawResponse,
                updatedAt = Dates.now(),
            ),
        )
    }

    suspend fun addManual(entry: ManualEntry, isCash: Boolean = true): Long = dao.insert(
        Payment(
            direction = entry.direction,
            amountPaise = entry.amountPaise,
            categoryId = entry.categoryId,
            contactId = entry.contactId,
            note = entry.note,
            status = PaymentStatus.SUCCESS,
            source = PaymentSource.MANUAL,
            txnRef = "MAN-" + UUID.randomUUID().toString(),
            createdAt = entry.at,
            updatedAt = Dates.now(),
            isCash = isCash,
        ),
    )

    suspend fun update(payment: Payment) = dao.update(payment.copy(updatedAt = Dates.now()))

    suspend fun setCategory(paymentId: Long, categoryId: Long?, note: String?) {
        val payment = dao.getById(paymentId) ?: return
        dao.update(payment.copy(categoryId = categoryId, note = note, updatedAt = Dates.now()))
    }

    suspend fun setStatus(paymentId: Long, status: PaymentStatus) {
        val payment = dao.getById(paymentId) ?: return
        dao.update(payment.copy(status = status, updatedAt = Dates.now()))
    }

    suspend fun delete(payment: Payment) = dao.delete(payment)

    companion object {
        /** Unique, short, alphanumeric reference accepted by every UPI app. */
        fun newTxnRef(): String = "KS" + System.currentTimeMillis().toString(36).uppercase() +
            UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
    }
}
