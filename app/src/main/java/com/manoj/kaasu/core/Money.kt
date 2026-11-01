package com.manoj.kaasu.core

import kotlin.math.abs

/** All money is stored as paise (Long) to avoid floating point errors. */
object Money {
    private const val PAISE_PER_RUPEE = 100L
    private val AMOUNT_INPUT = Regex("^\\d{1,9}(\\.\\d{0,2})?$")

    fun format(paise: Long, withSymbol: Boolean = true, forceDecimals: Boolean = false): String {
        val negative = paise < 0
        val absolute = abs(paise)
        val rupees = absolute / PAISE_PER_RUPEE
        val remainder = absolute % PAISE_PER_RUPEE
        val decimals = if (remainder != 0L || forceDecimals) "." + remainder.toString().padStart(2, '0') else ""
        val sign = if (negative) "-" else ""
        val symbol = if (withSymbol) "₹" else ""
        return sign + symbol + groupIndian(rupees) + decimals
    }

    /** 1234567 -> 12,34,567 (Indian digit grouping). */
    fun groupIndian(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + lastThree
    }

    /** Short label for chart axes: ₹950, ₹1.2k, ₹3.4L. */
    fun compact(paise: Long): String {
        val rupees = paise / PAISE_PER_RUPEE
        return when {
            rupees >= 100_000 -> "₹" + trimDecimal(rupees / 100_000.0) + "L"
            rupees >= 1_000 -> "₹" + trimDecimal(rupees / 1_000.0) + "k"
            else -> "₹$rupees"
        }
    }

    private fun trimDecimal(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    fun parseToPaise(input: String): Long? {
        val clean = input.replace(",", "").replace("₹", "").trim()
        if (clean.isEmpty() || !AMOUNT_INPUT.matches(clean)) return null
        val parts = clean.split(".")
        val rupees = parts[0].toLong()
        val fraction = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].padEnd(2, '0').toLong() else 0L
        return rupees * PAISE_PER_RUPEE + fraction
    }

    /** UPI expects amounts like "100.00". */
    fun toUpiAmount(paise: Long): String =
        "${paise / PAISE_PER_RUPEE}." + (paise % PAISE_PER_RUPEE).toString().padStart(2, '0')

    /** Plain editable text for an amount field: 100 or 100.5 */
    fun toInputText(paise: Long): String {
        val remainder = paise % PAISE_PER_RUPEE
        if (remainder == 0L) return (paise / PAISE_PER_RUPEE).toString()
        return toUpiAmount(paise).trimEnd('0')
    }
}
