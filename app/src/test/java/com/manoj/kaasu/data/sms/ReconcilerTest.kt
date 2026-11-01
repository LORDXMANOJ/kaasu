package com.manoj.kaasu.data.sms

import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentSource
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.repo.CategoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconcilerTest {
    private val t0 = 1_793_520_000_000L // a fixed moment
    private val minute = 60_000L
    private val categories = CategoryRepository.defaultCategories().mapIndexed { i, c -> c.copy(id = i + 1L) }
    private val payments = FakePaymentDao()
    private val sms = FakeSmsEventDao()
    private val contacts = FakeContactDao(
        listOf(Contact(id = 1, name = "Ramesh Kumar", vpa = "ramesh@okaxis", createdAt = 0)),
    )
    private val reconciler = Reconciler(payments, sms, contacts, FakeCategoryDao(categories))
    private var provider = 100L

    private fun category(kind: CategoryKind) = categories.first { it.kind == kind }.id

    private suspend fun appPayment(amount: Long, at: Long, status: PaymentStatus = PaymentStatus.PENDING, categoryId: Long? = 1, contactId: Long? = null): Long =
        payments.insert(
            Payment(
                direction = Direction.OUT,
                amountPaise = amount,
                payeeVpa = "shop@ybl",
                categoryId = categoryId,
                contactId = contactId,
                status = status,
                source = PaymentSource.APP,
                txnRef = "KS$at",
                createdAt = at,
                updatedAt = at,
            ),
        )

    private suspend fun receive(
        direction: Direction,
        amount: Long,
        at: Long,
        ref: String? = null,
        counterparty: String? = null,
        sender: String = "VM-SBIUPI",
    ): SmsEvent {
        val event = SmsEvent(
            providerId = provider++,
            sender = sender,
            body = "test",
            receivedAt = at,
            direction = direction,
            amountPaise = amount,
            refNo = ref,
            counterparty = counterparty,
        )
        val id = sms.insert(event)
        val saved = event.copy(id = id)
        reconciler.onSmsEvent(saved)
        return saved
    }

    @Test
    fun bankSmsConfirmsPendingPayment() = runTest {
        val id = appPayment(10_000, t0)
        receive(Direction.OUT, 10_000, t0 + minute, ref = "630512345678")
        val p = payments.getById(id)!!
        assertEquals(PaymentStatus.SUCCESS, p.status)
        assertEquals("630512345678", p.bankRef)
        assertEquals(1, payments.rows.value.size)
    }

    @Test
    fun smsRescuesPaymentTheUpiAppCalledFailed() = runTest {
        val id = appPayment(25_000, t0, status = PaymentStatus.FAILED)
        receive(Direction.OUT, 25_000, t0 + 2 * minute, ref = "630500000001")
        assertEquals(PaymentStatus.SUCCESS, payments.getById(id)!!.status)
    }

    @Test
    fun alreadySuccessfulPaymentIsLinkedNotDuplicated() = runTest {
        val id = appPayment(5_000, t0, status = PaymentStatus.SUCCESS)
        receive(Direction.OUT, 5_000, t0 + minute, ref = "630500000002")
        assertEquals(1, payments.rows.value.size)
        assertEquals("630500000002", payments.getById(id)!!.bankRef)
    }

    @Test
    fun closestPendingPaymentIsMatched() = runTest {
        val early = appPayment(2_000, t0)
        val late = appPayment(2_000, t0 + 20 * minute)
        receive(Direction.OUT, 2_000, t0 + 21 * minute, ref = "630500000003")
        assertEquals(PaymentStatus.SUCCESS, payments.getById(late)!!.status)
        assertEquals(PaymentStatus.PENDING, payments.getById(early)!!.status)
    }

    @Test
    fun paymentOutsideKaasuBecomesUntagged() = runTest {
        receive(Direction.OUT, 15_000, t0, ref = "630500000004", counterparty = "RAJU TEA STALL")
        val p = payments.rows.value.single()
        assertEquals(PaymentSource.SMS, p.source)
        assertNull(p.categoryId)
        assertEquals("RAJU TEA STALL", p.payeeName)
        assertEquals(PaymentStatus.SUCCESS, p.status)
    }

    @Test
    fun oldPendingPaymentIsNotMatchedToLaterSms() = runTest {
        val id = appPayment(3_000, t0)
        receive(Direction.OUT, 3_000, t0 + 3 * 60 * minute)
        assertEquals(PaymentStatus.PENDING, payments.getById(id)!!.status)
        assertEquals(2, payments.rows.value.size)
    }

    @Test
    fun duplicateSmsWithSameRefIsNotCountedTwice() = runTest {
        receive(Direction.OUT, 7_000, t0, ref = "630500000005")
        receive(Direction.OUT, 7_000, t0 + minute, ref = "630500000005")
        assertEquals(1, payments.rows.value.size)
    }

    @Test
    fun moneyFromFriendWhoOwesIsRepayment() = runTest {
        appPayment(50_000, t0, status = PaymentStatus.SUCCESS, categoryId = category(CategoryKind.LENT), contactId = 1)
        receive(Direction.IN, 20_000, t0 + 60 * minute, ref = "630500000006", counterparty = "ramesh@okaxis")
        val back = payments.rows.value.first { it.direction == Direction.IN }
        assertEquals(1L, back.contactId)
        assertEquals(category(CategoryKind.REPAYMENT), back.categoryId)
    }

    @Test
    fun friendMatchedByNameInSms() = runTest {
        appPayment(10_000, t0, status = PaymentStatus.SUCCESS, categoryId = category(CategoryKind.LENT), contactId = 1)
        receive(Direction.IN, 10_000, t0 + 60 * minute, counterparty = "RAMESH K")
        val back = payments.rows.value.first { it.direction == Direction.IN }
        assertEquals(1L, back.contactId)
        assertEquals(category(CategoryKind.REPAYMENT), back.categoryId)
    }

    @Test
    fun otherIncomingMoneyIsIncome() = runTest {
        receive(Direction.IN, 100_000, t0, ref = "630500000007", counterparty = "dad@oksbi")
        val p = payments.rows.value.single()
        assertEquals(category(CategoryKind.INCOME), p.categoryId)
        assertNull(p.contactId)
    }

    @Test
    fun notificationThenBankSmsCountsMoneyOnce() = runTest {
        receive(Direction.IN, 2_000, t0, counterparty = "Gukan Shanmug", sender = "notif:net.one97.paytm")
        receive(Direction.IN, 2_000, t0 + 2 * minute, ref = "630500000009", sender = "VM-SBIUPI")
        val credits = payments.rows.value.filter { it.direction == Direction.IN }
        assertEquals(1, credits.size)
        assertEquals("630500000009", credits.single().bankRef)
    }

    @Test
    fun twoNotificationsOfSameAmountAreTwoPayments() = runTest {
        receive(Direction.IN, 2_000, t0, counterparty = "Friend A", sender = "notif:net.one97.paytm")
        receive(Direction.IN, 2_000, t0 + minute, counterparty = "Friend B", sender = "notif:net.one97.paytm")
        assertEquals(2, payments.rows.value.size)
    }

    @Test
    fun notificationConfirmsPendingPaymentAndLaterSmsIsNotDuplicated() = runTest {
        val id = appPayment(100, t0)
        receive(Direction.OUT, 100, t0 + minute, counterparty = "Ragul", sender = "notif:com.phonepe.app")
        assertEquals(PaymentStatus.SUCCESS, payments.getById(id)!!.status)
        receive(Direction.OUT, 100, t0 + 3 * minute, ref = "630500000010", sender = "VM-SBIUPI")
        assertEquals(1, payments.rows.value.size)
    }

    @Test
    fun stalePendingBecomesUnconfirmed() = runTest {
        val old = appPayment(1_000, t0)
        val fresh = appPayment(1_000, t0 + 40 * minute)
        reconciler.sweepStale(now = t0 + 50 * minute)
        assertEquals(PaymentStatus.UNCONFIRMED, payments.getById(old)!!.status)
        assertEquals(PaymentStatus.PENDING, payments.getById(fresh)!!.status)
    }

    @Test
    fun unconfirmedPaymentStillGetsConfirmedLater() = runTest {
        val id = appPayment(4_000, t0)
        reconciler.sweepStale(now = t0 + 50 * minute)
        receive(Direction.OUT, 4_000, t0 + 3 * minute, ref = "630500000008")
        assertEquals(PaymentStatus.SUCCESS, payments.getById(id)!!.status)
    }
}
