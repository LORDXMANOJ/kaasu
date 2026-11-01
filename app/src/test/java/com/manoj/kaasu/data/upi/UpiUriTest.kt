package com.manoj.kaasu.data.upi

import com.manoj.kaasu.data.db.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiUriTest {

    @Test
    fun parsesShopQr() {
        val p = UpiUri.parse("upi://pay?pa=raju@okaxis&pn=Raju%20Tea%20Stall&mc=5411&tr=ABC123&cu=INR")
        assertNotNull(p)
        p!!
        assertEquals("raju@okaxis", p.vpa)
        assertEquals("Raju Tea Stall", p.name)
        assertEquals("5411", p.merchantCode)
        assertTrue(p.isMerchant)
        assertNull(p.amountPaise)
        assertFalse(p.isAmountLocked)
    }

    @Test
    fun personalQrIsNotMerchant() {
        val p = UpiUri.parse("upi://pay?pa=anu.k@oksbi&pn=Anu&mc=0000&mode=02")!!
        assertFalse(p.isMerchant)
    }

    @Test
    fun keepsShopQrParamsAndOnlyAddsAmountAndNote() {
        val p = UpiUri.parse("upi://pay?pa=raju@okaxis&pn=Raju%20Tea&mc=5411&tr=ABC&cu=INR")!!
        val link = UpiUri.buildPayLink(p, 10_000, "Snacks")
        assertEquals("upi://pay?pa=raju@okaxis&pn=Raju%20Tea&mc=5411&tr=ABC&cu=INR&am=100.00&tn=Snacks", link)
    }

    @Test
    fun replacesEditableAmountAndAddsCurrency() {
        val p = UpiUri.parse("upi://pay?pa=ab@okaxis&am=50")!!
        assertEquals(5_000L, p.amountPaise)
        assertFalse(p.isAmountLocked)
        val link = UpiUri.buildPayLink(p, 7_500, "Food")
        assertEquals("upi://pay?pa=ab@okaxis&am=75.00&cu=INR&tn=Food", link)
    }

    @Test
    fun signedQrWithAmountIsUsedUntouched() {
        val raw = "upi://pay?pa=shop@ybl&pn=Shop&am=250.00&sign=XYZ123&tn=Bill%2042"
        val p = UpiUri.parse(raw)!!
        assertTrue(p.isAmountLocked)
        assertEquals(raw, UpiUri.buildPayLink(p, 99_900, "Other"))
    }

    @Test
    fun signedQrWithoutAmountGetsAmountButNoNote() {
        val p = UpiUri.parse("upi://pay?pa=shop@ybl&pn=Shop&sign=XYZ")!!
        val link = UpiUri.buildPayLink(p, 2_000, "Snacks")
        assertEquals("upi://pay?pa=shop@ybl&pn=Shop&sign=XYZ&am=20.00&cu=INR", link)
    }

    @Test
    fun existingNoteIsNotOverwritten() {
        val p = UpiUri.parse("upi://pay?pa=ab@okaxis&tn=Order%20991")!!
        val link = UpiUri.buildPayLink(p, 1_000, "Snacks")
        assertEquals("upi://pay?pa=ab@okaxis&tn=Order%20991&am=10.00&cu=INR", link)
    }

    @Test
    fun normalisesUppercaseScheme() {
        val p = UpiUri.parse("UPI://PAY?pa=ab@okaxis&pn=AB")!!
        assertTrue(p.raw.startsWith("upi://pay?"))
    }

    @Test
    fun plainUpiIdIsAccepted() {
        val p = UpiUri.parse("friend@oksbi")!!
        assertEquals("friend@oksbi", p.vpa)
        assertEquals("upi://pay?pa=friend@oksbi", p.raw)
    }

    @Test
    fun rejectsNonUpiContent() {
        assertNull(UpiUri.parse("https://example.com"))
        assertNull(UpiUri.parse("upi://pay?pn=NoAddress"))
        assertNull(UpiUri.parse("upi://pay?pa=not-an-id"))
        assertNull(UpiUri.parse(""))
        assertNull(UpiUri.parse(null))
    }

    @Test
    fun parsesBharatQr() {
        fun tlv(tag: String, value: String) = tag + value.length.toString().padStart(2, '0') + value
        val merchantAccount = tlv("00", "A000000524") + tlv("01", "rajustores@ybl")
        val qr = tlv("00", "01") + tlv("01", "11") + tlv("26", merchantAccount) + tlv("52", "5411") +
            tlv("53", "356") + tlv("58", "IN") + tlv("59", "RAJU STORES") + tlv("60", "CHENNAI") + tlv("63", "ABCD")
        val p = UpiUri.parse(qr)!!
        assertEquals("rajustores@ybl", p.vpa)
        assertEquals("RAJU STORES", p.name)
        assertEquals("5411", p.merchantCode)
        assertTrue(p.raw.startsWith("upi://pay?pa=rajustores@ybl"))
    }

    @Test
    fun personLinkIsMinimalAndEncoded() {
        val link = UpiUri.buildPersonLink("anu.k@oksbi", "Anu K", 50_000, "Lent for bus")
        assertEquals("upi://pay?pa=anu.k@oksbi&pn=Anu%20K&am=500.00&cu=INR&tn=Lent%20for%20bus", link)
    }

    @Test
    fun noteIsSanitised() {
        assertEquals("Idli vada", UpiUri.sanitizeNote("Idli & vada"))
        assertNull(UpiUri.sanitizeNote("   "))
        assertEquals(50, UpiUri.sanitizeNote("x".repeat(80))!!.length)
    }

    @Test
    fun validatesUpiIds() {
        assertTrue(UpiUri.isValidVpa("manoj.n@okicici"))
        assertTrue(UpiUri.isValidVpa("9876543210@ybl"))
        assertFalse(UpiUri.isValidVpa("manoj"))
        assertFalse(UpiUri.isValidVpa("a@b"))
        assertFalse(UpiUri.isValidVpa("@ybl"))
    }

    @Test
    fun readsUpiAppResponses() {
        val ok = UpiResponseParser.parse("txnId=ABC123&responseCode=00&Status=SUCCESS&txnRef=KS1")
        assertEquals(PaymentStatus.SUCCESS, ok.status)
        assertEquals("ABC123", ok.txnId)
        assertEquals("00", ok.responseCode)

        val gpay = UpiResponseParser.parse("txnId=null&Status=Success&ApprovalRefNo=530512345678&responseCode=0")
        assertEquals(PaymentStatus.SUCCESS, gpay.status)
        assertNull(gpay.txnId)
        assertEquals("530512345678", gpay.approvalRef)

        assertEquals(PaymentStatus.FAILED, UpiResponseParser.parse("Status=FAILURE&responseCode=ZD").status)
        assertEquals(PaymentStatus.PENDING, UpiResponseParser.parse("status=submitted").status)
        assertNull(UpiResponseParser.parse(null).status)
        assertNull(UpiResponseParser.parse("").status)
    }
}
