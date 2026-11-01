package com.manoj.kaasu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun formatsWithIndianGrouping() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹5", Money.format(500))
        assertEquals("₹999", Money.format(99_900))
        assertEquals("₹1,000", Money.format(100_000))
        assertEquals("₹12,345", Money.format(1_234_500))
        assertEquals("₹1,23,456", Money.format(12_345_600))
        assertEquals("₹12,34,56,789", Money.format(123_456_789_00))
    }

    @Test
    fun formatsPaiseOnlyWhenNeeded() {
        assertEquals("₹100.50", Money.format(10_050))
        assertEquals("₹100.05", Money.format(10_005))
        assertEquals("₹100.00", Money.format(10_000, forceDecimals = true))
        assertEquals("-₹40", Money.format(-4_000))
        assertEquals("1,000", Money.format(100_000, withSymbol = false))
    }

    @Test
    fun parsesUserInput() {
        assertEquals(10_000L, Money.parseToPaise("100"))
        assertEquals(10_050L, Money.parseToPaise("100.5"))
        assertEquals(10_005L, Money.parseToPaise("100.05"))
        assertEquals(123_456_00L, Money.parseToPaise("1,23,456"))
        assertEquals(10_000L, Money.parseToPaise("₹ 100"))
        assertEquals(10_000L, Money.parseToPaise("100."))
        assertNull(Money.parseToPaise(""))
        assertNull(Money.parseToPaise("abc"))
        assertNull(Money.parseToPaise("1.234"))
        assertNull(Money.parseToPaise("-5"))
    }

    @Test
    fun upiAmountAlwaysHasTwoDecimals() {
        assertEquals("100.00", Money.toUpiAmount(10_000))
        assertEquals("0.50", Money.toUpiAmount(50))
        assertEquals("1250.75", Money.toUpiAmount(125_075))
    }

    @Test
    fun inputTextRoundTrips() {
        assertEquals("100", Money.toInputText(10_000))
        assertEquals("100.5", Money.toInputText(10_050))
        assertEquals("100.05", Money.toInputText(10_005))
    }

    @Test
    fun compactLabels() {
        assertEquals("₹950", Money.compact(95_000))
        assertEquals("₹1.2k", Money.compact(123_400))
        assertEquals("₹2k", Money.compact(200_000))
        assertEquals("₹3.4L", Money.compact(34_000_000))
    }
}
