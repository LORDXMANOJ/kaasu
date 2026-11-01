package com.manoj.kaasu.data.sms

import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.data.db.CategoryDao
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.db.ContactDao
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentDao
import com.manoj.kaasu.data.db.PaymentSource
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.db.SmsEventDao
import com.manoj.kaasu.domain.DebtCalculator
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Silently settles payments from bank SMS. It never asks the user anything:
 * pending app payments become Paid, payments made outside the app appear as Untagged,
 * and money received from a friend who owes you is counted as a repayment.
 */
@Singleton
class Reconciler @Inject constructor(
    private val paymentDao: PaymentDao,
    private val smsEventDao: SmsEventDao,
    private val contactDao: ContactDao,
    private val categoryDao: CategoryDao,
) {
    suspend fun onSmsEvent(event: SmsEvent) {
        val byRef = event.refNo?.let { paymentDao.findByRef(it) }
        if (byRef != null && byRef.direction == event.direction) {
            link(byRef, event)
            return
        }
        if (linkToSameMoneySeenOnOtherChannel(event)) return
        if (event.direction == Direction.OUT) handleDebit(event) else handleCredit(event)
    }

    /**
     * The same money can be announced twice: by a UPI-app notification and later by the bank SMS.
     * If a payment of the same amount was already settled by the other channel a moment ago, reuse it.
     */
    private suspend fun linkToSameMoneySeenOnOtherChannel(event: SmsEvent): Boolean {
        val nearby = paymentDao.getBetween(event.receivedAt - DUPLICATE_WINDOW_MS, event.receivedAt + DUPLICATE_WINDOW_MS)
        for (payment in nearby) {
            if (payment.direction != event.direction || payment.amountPaise != event.amountPaise) continue
            val otherEvent = payment.smsEventId?.let { smsEventDao.getById(it) } ?: continue
            if (isNotification(otherEvent) == isNotification(event)) continue
            if (payment.bankRef == null && event.refNo != null) paymentDao.update(payment.copy(bankRef = event.refNo, updatedAt = Dates.now()))
            smsEventDao.update(event.copy(paymentId = payment.id))
            return true
        }
        return false
    }

    private fun isNotification(event: SmsEvent): Boolean = event.sender.startsWith(NOTIFICATION_SENDER_PREFIX)

    private suspend fun handleDebit(event: SmsEvent) {
        val candidates = paymentDao.findUnlinked(
            Direction.OUT,
            event.amountPaise,
            event.receivedAt - MATCH_BEFORE_MS,
            event.receivedAt + MATCH_AFTER_MS,
        ).filter { it.source == PaymentSource.APP }
        val match = candidates.minByOrNull { abs(it.createdAt - event.receivedAt) }
        if (match != null) {
            link(match, event)
            return
        }
        val contact = findContact(event.counterparty)
        insertFromSms(event, categoryId = null, contact = contact)
    }

    private suspend fun handleCredit(event: SmsEvent) {
        val contact = findContact(event.counterparty)
        val categories = categoryDao.getAll().associateBy { it.id }
        val owes = contact?.let {
            DebtCalculator.summarize(paymentDao.observeByContact(it.id).first(), categories).outstandingPaise > 0
        } ?: false
        val kind = if (owes) CategoryKind.REPAYMENT else CategoryKind.INCOME
        insertFromSms(event, categoryDao.firstOfKind(kind)?.id, contact)
    }

    private suspend fun insertFromSms(event: SmsEvent, categoryId: Long?, contact: Contact?) {
        val counterparty = event.counterparty
        val isVpa = counterparty?.contains('@') == true
        val now = Dates.now()
        val id = paymentDao.insert(
            Payment(
                direction = event.direction,
                amountPaise = event.amountPaise,
                payeeVpa = if (isVpa) counterparty else contact?.vpa,
                payeeName = contact?.name ?: if (isVpa) null else counterparty,
                categoryId = categoryId,
                contactId = contact?.id,
                status = PaymentStatus.SUCCESS,
                source = PaymentSource.SMS,
                txnRef = "SMS-${event.providerId}",
                bankRef = event.refNo,
                createdAt = event.receivedAt,
                updatedAt = now,
                smsEventId = event.id,
            ),
        )
        smsEventDao.update(event.copy(paymentId = id))
    }

    private suspend fun link(payment: Payment, event: SmsEvent) {
        paymentDao.update(
            payment.copy(
                status = PaymentStatus.SUCCESS,
                bankRef = payment.bankRef ?: event.refNo,
                smsEventId = payment.smsEventId ?: event.id,
                updatedAt = Dates.now(),
            ),
        )
        smsEventDao.update(event.copy(paymentId = payment.id))
    }

    private suspend fun findContact(counterparty: String?): Contact? {
        if (counterparty.isNullOrBlank()) return null
        if (counterparty.contains('@')) return contactDao.findByVpa(counterparty)
        val needle = normalise(counterparty)
        if (needle.length < 3) return null
        val matches = contactDao.getActive().filter { contact ->
            val name = normalise(contact.name)
            val first = name.substringBefore(' ')
            name == needle || needle.contains(name) || (first.length >= 3 && needle.split(' ').contains(first))
        }
        return matches.singleOrNull()
    }

    private fun normalise(value: String): String =
        value.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Pending payments that never got a bank SMS become "Unconfirmed" (shown quietly in History). */
    suspend fun sweepStale(now: Long = Dates.now()) {
        paymentDao.getPendingOlderThan(now - STALE_AFTER_MS).forEach {
            paymentDao.update(it.copy(status = PaymentStatus.UNCONFIRMED, updatedAt = now))
        }
    }

    companion object {
        const val MATCH_BEFORE_MS = 45 * 60_000L
        const val MATCH_AFTER_MS = 5 * 60_000L
        const val STALE_AFTER_MS = 45 * 60_000L
        const val DUPLICATE_WINDOW_MS = 10 * 60_000L
        const val NOTIFICATION_SENDER_PREFIX = "notif:"
    }
}
