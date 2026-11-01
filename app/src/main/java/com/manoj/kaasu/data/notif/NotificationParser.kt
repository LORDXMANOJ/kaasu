package com.manoj.kaasu.data.notif

import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.sms.ParsedSms

/**
 * Understands the "money received / paid" notifications shown by UPI apps (Paytm, PhonePe, GPay, BHIM).
 * Only messages that clearly describe a completed payment are accepted; everything else returns null.
 */
object NotificationParser {
    /** The only apps whose notifications Kaasu ever looks at. Everything else is dropped unread. */
    val UPI_PACKAGES: Set<String> = setOf(
        "net.one97.paytm",
        "com.phonepe.app",
        "money.super.payments",
        "com.google.android.apps.nbu.paisa.user",
        "in.org.npci.upiapp",
        "com.sbi.lotusintouch",
        "com.sbi.upi",
    )

    private const val AMT = "(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    private const val NAME = "([A-Za-z][A-Za-z .&'\\-]{1,39}?)"
    private const val END = "(?=\\s*(?:…|\\.{2,}|\\.(?=\\s|$)|,|\\n|$)|\\s+(?:on|via|using|from|to|has|in|for|at)\\b)"
    private val OPT = setOf(RegexOption.IGNORE_CASE)

    private class Rule(val regex: Regex, val direction: Direction, val amountGroup: Int, val nameGroup: Int)

    private val RULES = listOf(
        // "Received ₹20 from A Friend…"
        Rule(Regex("(?:received|credited|deposited)\\s+(?:rs\\.?\\s*)?$AMT(?:\\s+from\\s+$NAME$END)?", OPT), Direction.IN, 1, 2),
        // "₹17.68 received from super.money" / "₹500 credited to your account from Ravi"
        Rule(Regex("$AMT\\s+(?:has been\\s+|was\\s+)?(?:received|credited|deposited)(?:\\s+(?:to|in)\\s+your\\s+[a-z ]+?)?(?:\\s+from\\s+([A-Za-z][A-Za-z .&'\\-]{1,39}?)$END)?", OPT), Direction.IN, 1, 2),
        // PhonePe: "₹40 has been sent to gpay-12202392826@okbizaxis" / "₹40 paid to Raju Tea"
        Rule(Regex("$AMT\\s+(?:has been\\s+|was\\s+)?(?:sent|paid|debited|transferred)\\s+to\\s+([A-Za-z0-9._\\-]{2,64}@[A-Za-z][A-Za-z0-9]{1,30}|[A-Za-z][A-Za-z .&'\\-]{1,39}?$END)", OPT), Direction.OUT, 1, 2),
        // "P S SIVIN has sent ₹100 to your bank account ..."  /  "Ravi paid you ₹50"
        Rule(Regex("^\\s*$NAME\\s+(?:has\\s+)?(?:sent|paid|transferred)\\s+(?:you\\s+)?$AMT", OPT), Direction.IN, 2, 1),
        // "Paid ₹40 to Raju Tea" / "Sent ₹40 to Raju" / "Payment of ₹40 to Raju successful"
        Rule(Regex("(?:paid|sent|transferred|payment of)\\s+$AMT\\s+(?:to|at)\\s+$NAME$END", OPT), Direction.OUT, 1, 2),
        // "You paid Raju ₹40"
        Rule(Regex("you\\s+(?:paid|sent)\\s+$NAME\\s+$AMT", OPT), Direction.OUT, 2, 1),
    )

    /** Never a completed payment. */
    private val REJECT = listOf(
        "request", "failed", "declined", "unsuccessful", "pending", "expire", "reminder", "will be", "due",
        "otp", "recharge now", "pay now",
    )

    /** Promotions. A clear "Paid ₹X to NAME" alert may still mention cashback (super.money does), so only money-in is blocked. */
    private val PROMO = listOf(
        "cashback", "reward", "offer", "scratch", "won ", "earn", "claim", "refer", "get ₹", "get rs",
        "save ₹", "save rs", "upto", "up to", "coupon",
    )

    fun parse(title: String, text: String): ParsedSms? {
        val combined = "$title\n$text"
        val lower = combined.lowercase()
        if (REJECT.any { lower.contains(it) }) return null
        val parsed = match(title, text, combined) ?: return null
        if (parsed.direction == Direction.IN && PROMO.any { lower.contains(it) }) return null
        return parsed
    }

    /** Why an alert was skipped, for the status line in Settings. */
    fun whySkipped(title: String, text: String): String {
        val combined = "$title\n$text"
        val lower = combined.lowercase()
        REJECT.firstOrNull { lower.contains(it) }?.let { return "has \"$it\"" }
        val parsed = match(title, text, combined) ?: return "no pattern (${title.take(28)} | ${text.take(40)})"
        PROMO.firstOrNull { lower.contains(it) }?.let { return "promo word \"$it\"" }
        return "ok ${parsed.amountPaise}"
    }

    private fun match(title: String, text: String, combined: String): ParsedSms? {
        for (segment in listOf(text, title, combined)) {
            for (rule in RULES) {
                val match = rule.regex.find(segment) ?: continue
                val amount = match.groupValues[rule.amountGroup].let(::amountToPaise) ?: continue
                if (amount <= 0) continue
                val name = match.groups[rule.nameGroup]?.value?.let(::cleanName)
                return ParsedSms(
                    direction = rule.direction,
                    amountPaise = amount,
                    balancePaise = null,
                    refNo = null,
                    counterparty = name,
                    account = null,
                )
            }
        }
        return null
    }

    private fun amountToPaise(raw: String): Long? = Money.parseToPaise(raw.replace(",", "").trimEnd('.'))

    private fun cleanName(raw: String): String? =
        raw.replace(Regex("\\s+"), " ").trim().trimEnd('.', '-', ',', '…').takeIf { it.length >= 2 }
}
