package com.manoj.kaasu.data.sms

import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.Direction
import java.time.Instant
import java.time.ZoneId

data class ParsedSms(
    val direction: Direction,
    val amountPaise: Long,
    val balancePaise: Long?,
    val refNo: String?,
    val counterparty: String?,
    val account: String?,
)

/**
 * Understands Indian bank transaction SMS (SBI, HDFC, ICICI, Axis, Kotak, Canara, IOB, ...).
 * Only messages that clearly describe a completed debit/credit are accepted.
 */
object SmsParser {
    private val OPT = setOf(RegexOption.IGNORE_CASE)
    private const val NUM = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    private const val CUR = "(?:rs\\.?|inr|₹)"
    private const val VERBS = "debited|credited|sent|received|paid|spent|withdrawn|transferred|deposited|deducted|refunded|added"

    private val DEBIT_WORDS = Regex("\\b(debited|debit|sent|paid|spent|withdrawn|transferred|deducted|purchase)\\b", OPT)
    private val CREDIT_WORDS = Regex("\\b(credited|received|deposited|refunded|refund|reversed|added)\\b", OPT)

    private val AMOUNT_AFTER_VERB = Regex("\\b(?:$VERBS)\\b(?:\\s+(?:by|with|for|of))?\\s*(?:an amount of\\s*)?$CUR?\\s*$NUM", OPT)
    private val AMOUNT_BEFORE_VERB = Regex("$CUR\\s*$NUM\\s*(?:has been|have been|is|was|been)?\\s*(?:successfully\\s+)?(?:$VERBS)", OPT)
    private val ANY_AMOUNT = Regex("$CUR\\s*$NUM", OPT)
    private val BALANCE = Regex(
        "(?:avl\\.?|avbl\\.?|available|avail\\.?|clr\\.?|total)?\\s*bal(?:ance)?\\.?\\s*(?:is|:|-|of)?\\s*:?\\s*$CUR\\s*:?\\s*(-?[0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        OPT,
    )

    private val REF_PATTERNS = listOf(
        Regex("upi\\s*/\\s*(?:p2[ma]|[a-z]{2,6})\\s*/\\s*(\\d{9,18})", OPT),
        Regex("(?:utr|rrn|ref(?:erence)?|txn|transaction)\\s*(?:no|num|number|id)?\\.?\\s*[:#\\-]?\\s*(\\d{9,18})", OPT),
        Regex("upi\\s*[:\\-]?\\s*(\\d{12})", OPT),
    )
    private val TWELVE_DIGITS = Regex("(?<!\\d)(\\d{12})(?!\\d)")

    private val VPA = Regex("[a-zA-Z0-9._\\-]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,64}")
    private const val NAME = "([A-Za-z0-9][A-Za-z0-9 .&'_\\-]{1,39}?)"
    private val NAME_PATTERNS = listOf(
        Regex("upi\\s*/\\s*(?:p2[ma]|[a-z]{2,6})\\s*/\\s*\\d{9,18}\\s*/\\s*([^/\\n]{2,40})", OPT),
        Regex("(?:trf|transfer(?:red)?|sent|paid)\\s+to\\s+$NAME(?=\\s+(?:ref|on\\b|upi|via|from|a/c)|\\s*[.\\n(]|$)", OPT),
        Regex("\\bto\\s+$NAME\\s*(?:\\n|\\s+on\\b)", OPT),
        Regex("(?:transfer\\s+from|received\\s+from|from|by)\\s+$NAME(?=\\s*\\(|\\s+(?:ref|on\\b|upi|via)|\\s*[.\\n]|$)", OPT),
    )
    private val BAD_NAME = Regex(
        "^(a/c|ac\\b|acct|account|your|you\\b|ur\\b|the\\b|bank\\b|xx|[x*]+\\d|\\d+$|hdfc bank|sbi\\b|icici|axis|kotak|(?:rs|inr|₹)\\.?\\s*\\d|rs\\b|inr\\b)|\\d{4,}",
        OPT,
    )

    private val ACCOUNT = Regex("(?:a/c|acct|account|\\bac)\\s*(?:no\\.?)?\\s*[:\\-]?\\s*(?:[x*]+|ending\\s*(?:with)?\\s*)?\\s*(\\d{3,6})\\b", OPT)
    private val MASKED_ACCOUNT = Regex("\\b[x*]{2,}(\\d{3,6})\\b", OPT)

    private val REJECT = listOf(
        "otp", "one time password", "will be debited", "will be credited", "to be debited", "is due", "due on",
        "due date", "due by", "has requested", "collect request", "payment request", "requested money", "mandate",
        "autopay", "minimum amount", "statement", "failed", "declined", "unsuccessful", "could not", "insufficient",
        "pre-approved", "is pending", "cashback upto", "win ", "offer",
    )
    private val BANK_CONTEXT = listOf(
        "a/c", "acct", "account", "upi", "bank", "card", "wallet", "imps", "neft", "rtgs", "vpa", "ac x", "ac no",
    )
    private val PHONE_SENDER = Regex("^\\+?\\d{8,15}$")

    fun parse(sender: String, body: String, receivedAt: Long): ParsedSms? {
        if (PHONE_SENDER.matches(sender.replace(" ", ""))) return null
        val text = body.replace('\r', '\n')
        val lower = text.lowercase()
        if (REJECT.any { lower.contains(it) }) return null
        if (BANK_CONTEXT.none { lower.contains(it) }) return null
        val direction = detectDirection(text) ?: return null
        val amount = findAmount(text) ?: return null
        if (amount <= 0) return null
        return ParsedSms(
            direction = direction,
            amountPaise = amount,
            balancePaise = BALANCE.find(text)?.groupValues?.get(1)?.let { parseAmount(it) },
            refNo = findRef(text, receivedAt),
            counterparty = findCounterparty(text),
            account = ACCOUNT.find(text)?.groupValues?.get(1) ?: MASKED_ACCOUNT.find(text)?.groupValues?.get(1),
        )
    }

    /**
     * A balance reply with no transaction in it, e.g. SBI's missed-call balance enquiry:
     * "Your A/C XXXXX1890 has an available balance of INR 542.04 as on 01-11-26".
     */
    fun parseBalanceOnly(sender: String, body: String): Long? {
        if (PHONE_SENDER.matches(sender.replace(" ", ""))) return null
        val text = body.replace('\r', '\n')
        val lower = text.lowercase()
        if (!lower.contains("bal")) return null
        if (REJECT.any { lower.contains(it) } || BALANCE_REJECT.any { lower.contains(it) }) return null
        if (BANK_CONTEXT.none { lower.contains(it) }) return null
        if (detectDirection(text) != null) return null
        val raw = BALANCE.find(text)?.groupValues?.get(1) ?: LOOSE_BALANCE.find(text)?.groupValues?.get(1)
        return raw?.let { parseAmount(it) }
    }

    /** "Avl Bal in A/c XX1890 is Rs.1,234.50": the amount comes a few words after "bal". */
    private val LOOSE_BALANCE = Regex("\\bbal(?:ance)?\\b[^₹\\n]{0,40}?$CUR\\s*:?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", OPT)

    private val BALANCE_REJECT = listOf("minimum", "maintain", "below", "charges", "penalty", "low balance")

    private fun detectDirection(text: String): Direction? {
        val debit = DEBIT_WORDS.find(text)?.range?.first
        val credit = CREDIT_WORDS.find(text)?.range?.first
        return when {
            debit == null && credit == null -> null
            credit == null -> Direction.OUT
            debit == null -> Direction.IN
            debit < credit -> Direction.OUT
            else -> Direction.IN
        }
    }

    private fun findAmount(text: String): Long? {
        val anchored = listOfNotNull(AMOUNT_AFTER_VERB.find(text), AMOUNT_BEFORE_VERB.find(text))
            .minByOrNull { it.range.first }
        anchored?.groupValues?.get(1)?.let { parseAmount(it) }?.let { return it }
        val balanceRange = BALANCE.find(text)?.range
        return ANY_AMOUNT.findAll(text)
            .filter { match -> balanceRange == null || match.range.first !in balanceRange }
            .filter { match -> !text.substring(maxOf(0, match.range.first - 20), match.range.first).contains("limit", true) }
            .firstNotNullOfOrNull { parseAmount(it.groupValues[1]) }
    }

    private fun parseAmount(raw: String): Long? {
        val clean = raw.replace(",", "").trimEnd('.')
        val parts = clean.split(".")
        val normalised = if (parts.size > 1) parts[0] + "." + parts[1].take(2) else parts[0]
        return Money.parseToPaise(normalised.removePrefix("-"))
    }

    private fun findRef(text: String, receivedAt: Long): String? {
        REF_PATTERNS.forEach { pattern -> pattern.find(text)?.let { return it.groupValues[1] } }
        val year = Instant.ofEpochMilli(receivedAt).atZone(ZoneId.systemDefault()).year
        val allowedFirstDigits = setOf((year % 10).digitToChar(), ((year - 1) % 10).digitToChar())
        return TWELVE_DIGITS.findAll(text).map { it.groupValues[1] }.firstOrNull { candidate ->
            val dayOfYear = candidate.substring(1, 4).toIntOrNull() ?: 0
            candidate[0] in allowedFirstDigits && dayOfYear in 1..366
        }
    }

    private fun findCounterparty(text: String): String? {
        VPA.find(text)?.value?.let { return it }
        NAME_PATTERNS.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                val candidate = match.groupValues[1].replace(Regex("\\s+"), " ").trim().trimEnd('.', '-', ',')
                if (candidate.length >= 2 && !BAD_NAME.containsMatchIn(candidate) && !candidate.contains("a/c", true)) {
                    return candidate
                }
            }
        }
        return null
    }
}
