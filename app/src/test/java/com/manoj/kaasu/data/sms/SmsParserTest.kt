package com.manoj.kaasu.data.sms

import com.manoj.kaasu.data.db.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SmsParserTest {
    /** 1 Nov 2026, so 12-digit UPI refs starting with 6 (or 5 for last year) are recognised. */
    private val now = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun parse(body: String, sender: String = "VM-BANKSM") = SmsParser.parse(sender, body, now)

    @Test
    fun sbiMissedCallBalanceReply() {
        val body = "Dear Customer, Your A/c XXXXXXX1890 has an available balance of INR 542.04 as on 01/11/2026 23:30:00. Download YONO SBI"
        assertNull(parse(body, "JK-SBIBNK-S"))
        assertEquals(54_204L, SmsParser.parseBalanceOnly("JK-SBIBNK-S", body))
        assertEquals(1_23_450L, SmsParser.parseBalanceOnly("AD-SBIINB", "Avl Bal in A/c XX1890 is Rs.1,234.50 as on 01Nov26"))
    }

    @Test
    fun balanceWarningsAreNotBalances() {
        assertNull(SmsParser.parseBalanceOnly("AD-SBIINB", "Maintain minimum balance of Rs 1000 in your A/c XX1890 to avoid charges"))
        assertNull(SmsParser.parseBalanceOnly("9876543210", "Your A/c balance is Rs 500"))
    }

    @Test
    fun upiNumberFromPhone() {
        assertEquals("9876543210@upi", com.manoj.kaasu.data.upi.UpiUri.upiNumberVpa("+91 98765 43210"))
        assertEquals("9876543210@upi", com.manoj.kaasu.data.upi.UpiUri.upiNumberVpa("09876543210"))
        assertNull(com.manoj.kaasu.data.upi.UpiUri.upiNumberVpa("12345"))
        assertNull(com.manoj.kaasu.data.upi.UpiUri.upiNumberVpa("1234567890"))
    }

    @Test
    fun sbiUpiDebit() {
        val p = parse("Dear UPI user A/C X1234 debited by 100.0 on date 01Nov26 trf to RAJU TEA STALL Refno 630512345678. If not u? call 1800111109. -SBI")
        assertNotNull(p)
        p!!
        assertEquals(Direction.OUT, p.direction)
        assertEquals(10_000L, p.amountPaise)
        assertEquals("630512345678", p.refNo)
        assertEquals("RAJU TEA STALL", p.counterparty)
        assertEquals("1234", p.account)
        assertNull(p.balancePaise)
    }

    @Test
    fun sbiUpiCredit() {
        val p = parse("Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 01Nov26 by  (Ref no 630598765432)")!!
        assertEquals(Direction.IN, p.direction)
        assertEquals(50_000L, p.amountPaise)
        assertEquals("630598765432", p.refNo)
        assertNull(p.counterparty)
    }

    @Test
    fun hdfcSentMultiLine() {
        val p = parse("Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 01/11/26\nRef 630511112222\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808")!!
        assertEquals(Direction.OUT, p.direction)
        assertEquals(25_000L, p.amountPaise)
        assertEquals("630511112222", p.refNo)
        assertEquals("SWIGGY", p.counterparty)
        assertEquals("1234", p.account)
    }

    @Test
    fun hdfcReceivedFromUpiId() {
        val p = parse("Received Rs.500.00 in your HDFC Bank A/c xx1234 from ramesh@okaxis on 01-11-26 (UPI Ref 630533334444)")!!
        assertEquals(Direction.IN, p.direction)
        assertEquals(50_000L, p.amountPaise)
        assertEquals("ramesh@okaxis", p.counterparty)
        assertEquals("630533334444", p.refNo)
    }

    @Test
    fun iciciDebitWithUpiRef() {
        val p = parse("ICICI Bank Acct XX123 debited for Rs 150.00 on 01-Nov-26; Merchant credited. UPI:630544445555. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.")!!
        assertEquals(Direction.OUT, p.direction)
        assertEquals(15_000L, p.amountPaise)
        assertEquals("630544445555", p.refNo)
        assertEquals("123", p.account)
    }

    @Test
    fun axisAmountBeforeVerb() {
        val p = parse("INR 200.00 debited\nA/c no. XX1234\n01-11-26, 10:15:22\nUPI/P2M/630555556666/ZOMATO\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank")!!
        assertEquals(Direction.OUT, p.direction)
        assertEquals(20_000L, p.amountPaise)
        assertEquals("630555556666", p.refNo)
        assertEquals("ZOMATO", p.counterparty)
    }

    @Test
    fun kotakSentToUpiId() {
        val p = parse("Sent Rs.120.00 from Kotak Bank AC X1234 to merchant@ybl on 01-11-26.UPI Ref 630566667777. Not you, https://kotak.com/KBANKT/Fraud")!!
        assertEquals(12_000L, p.amountPaise)
        assertEquals("merchant@ybl", p.counterparty)
        assertEquals("630566667777", p.refNo)
    }

    @Test
    fun balanceIsReadButNotMistakenForAmount() {
        val p = parse("Your a/c XX1234 is debited for Rs.100.00 on 01-11-2026 and credited to a/c XX5678 (UPI Ref no 630577778888). Avl Bal Rs.5,432.10")!!
        assertEquals(Direction.OUT, p.direction)
        assertEquals(10_000L, p.amountPaise)
        assertEquals(543_210L, p.balancePaise)
        assertEquals("630577778888", p.refNo)
    }

    @Test
    fun balanceWithColonAndInr() {
        val p = parse("Your A/c XX1234 has been debited with Rs 1,250.00 on 01-11-26 towards UPI. Avl Bal: INR 10,234.55")!!
        assertEquals(125_000L, p.amountPaise)
        assertEquals(1_023_455L, p.balancePaise)
    }

    @Test
    fun refundIsCredit() {
        val p = parse("Rs 299 refunded to your a/c XX1234 for your order 12345")!!
        assertEquals(Direction.IN, p.direction)
        assertEquals(29_900L, p.amountPaise)
    }

    @Test
    fun ignoresNonTransactions() {
        assertNull(parse("123456 is your OTP for txn of Rs 500.00 at AMAZON on HDFC Bank card xx1234"))
        assertNull(parse("Your Airtel bill of Rs 499 is due on 05-Nov. Pay via UPI"))
        assertNull(parse("RAVI has requested money of Rs 300 from you on UPI. Approve in app"))
        assertNull(parse("Get cashback upto Rs 100 on UPI payments! T&C apply"))
        assertNull(parse("Your UPI transaction of Rs 200 from a/c XX1234 failed"))
        assertNull(parse("Hi, can you send Rs 100?"))
    }

    @Test
    fun ignoresMessagesFromPersonalNumbers() {
        assertNull(SmsParser.parse("+919876543210", "Your a/c XX1234 debited by Rs 100", now))
        assertNull(SmsParser.parse("9876543210", "Your a/c XX1234 debited by Rs 100", now))
    }

    @Test
    fun fallsBackToTwelveDigitRrn() {
        val p = parse("Rs.75.00 paid from a/c XX9876 to CHAI POINT. 630112223333. -Bank")!!
        assertEquals(7_500L, p.amountPaise)
        assertEquals("630112223333", p.refNo)
    }

    @Test
    fun phoneNumbersAreNotTakenAsRefs() {
        val p = parse("INR 50.00 debited from A/c XX1234. Not you? SMS BLOCK to 919951860002")!!
        assertNull(p.refNo)
    }
}
