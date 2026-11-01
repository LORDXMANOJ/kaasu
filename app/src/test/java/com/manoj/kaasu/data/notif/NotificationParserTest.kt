package com.manoj.kaasu.data.notif

import com.manoj.kaasu.data.db.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {
    @Test
    fun paytmReceivedWithTruncatedName() {
        val p = NotificationParser.parse("Received ₹20 from Gukan Shanmug…", "Deposited in your State Bank Of India - 1890")
        assertNotNull(p)
        assertEquals(Direction.IN, p!!.direction)
        assertEquals(2_000L, p.amountPaise)
        assertEquals("Gukan Shanmug", p.counterparty)
    }

    @Test
    fun paytmRealAlertFromPhone() {
        val p = NotificationParser.parse(
            "Received ₹500 from Selvaraj I",
            "Deposited in your State Bank Of India - 1890 on 01 November at 10:08 PM",
        )
        assertNotNull(p)
        assertEquals(Direction.IN, p!!.direction)
        assertEquals(50_000L, p.amountPaise)
        assertEquals("Selvaraj I", p.counterparty)
    }

    @Test
    fun superMoneyAmountFirst() {
        val p = NotificationParser.parse("₹17.68 received from super.money", "")
        assertNotNull(p)
        assertEquals(Direction.IN, p!!.direction)
        assertEquals(1_768L, p.amountPaise)
        assertEquals("super.money", p.counterparty)
    }

    @Test
    fun phonePeMoneySentToUpiId() {
        val p = NotificationParser.parse("Money transfer successful", "₹40 has been sent to gpay-12202392826@okbizaxis")
        assertNotNull(p)
        assertEquals(Direction.OUT, p!!.direction)
        assertEquals(4_000L, p.amountPaise)
        assertEquals("gpay-12202392826@okbizaxis", p.counterparty)
    }

    @Test
    fun phonePeMoneyReceivedLongText() {
        val p = NotificationParser.parse("Money received", "MOHAMED ISHAM M has sent ₹205 to your bank account State Bank of India-1890")
        assertEquals(20_500L, p!!.amountPaise)
        assertEquals("MOHAMED ISHAM M", p.counterparty)
    }

    @Test
    fun phonePeMoneyReceived() {
        val p = NotificationParser.parse("Money received", "P S SIVIN has sent ₹100 to your bank account State Bank of India-1890")
        assertNotNull(p)
        assertEquals(Direction.IN, p!!.direction)
        assertEquals(10_000L, p.amountPaise)
        assertEquals("P S SIVIN", p.counterparty)
    }

    @Test
    fun paidToShop() {
        val p = NotificationParser.parse("Payment successful", "Paid ₹40 to Raju Tea Stall")
        assertNotNull(p)
        assertEquals(Direction.OUT, p!!.direction)
        assertEquals(4_000L, p.amountPaise)
        assertEquals("Raju Tea Stall", p.counterparty)
    }

    @Test
    fun paymentAlertThatMentionsCashbackStillCounts() {
        val p = NotificationParser.parse("Payment successful", "Paid ₹40 to Raju Tea Stall. You earned ₹2 cashback")
        assertNotNull(p)
        assertEquals(Direction.OUT, p!!.direction)
        assertEquals(4_000L, p.amountPaise)
    }

    @Test
    fun cashbackCreditIsNotIncome() {
        assertNull(NotificationParser.parse("Cashback received", "Received ₹2 cashback in your account"))
    }

    @Test
    fun amountWithCommasAndPaise() {
        val p = NotificationParser.parse("Received ₹1,250.50 from Ramesh", "")
        assertEquals(125_050L, p!!.amountPaise)
    }

    @Test
    fun rejectsRequestsOffersAndFailures() {
        assertNull(NotificationParser.parse("Ravi requested ₹200", "Tap to pay"))
        assertNull(NotificationParser.parse("Get ₹50 cashback", "Pay your bill now and earn rewards"))
        assertNull(NotificationParser.parse("Payment failed", "Paid ₹40 to Raju could not be completed, failed"))
        assertNull(NotificationParser.parse("Spin and win", "You have won ₹100"))
    }

    @Test
    fun ignoresNonPaymentText() {
        assertNull(NotificationParser.parse("PhonePe", "Check your credit score for free"))
        assertNull(NotificationParser.parse("", ""))
    }

    @Test
    fun onlyUpiAppsAreAllowed() {
        assert("net.one97.paytm" in NotificationParser.UPI_PACKAGES)
        assert("com.whatsapp" !in NotificationParser.UPI_PACKAGES)
    }
}
