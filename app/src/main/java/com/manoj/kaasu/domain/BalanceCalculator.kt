package com.manoj.kaasu.domain

import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.db.SmsEvent

data class BalanceInfo(
    val amountPaise: Long,
    val asOf: Long,
    /** True when we adjusted the last known figure with later payments. */
    val isEstimate: Boolean,
    val fromSms: Boolean,
)

/**
 * No app can read a live bank balance without the bank PIN, so we use the last balance a bank SMS
 * mentioned (or the balance you typed in) and adjust it with the payments seen since then.
 */
object BalanceCalculator {
    fun compute(
        latestSmsWithBalance: SmsEvent?,
        openingPaise: Long?,
        openingAt: Long?,
        payments: List<Payment>,
    ): BalanceInfo? {
        val smsAnchor = latestSmsWithBalance?.balancePaise?.let { Triple(it, latestSmsWithBalance.receivedAt, true) }
        val manualAnchor = if (openingPaise != null && openingAt != null) Triple(openingPaise, openingAt, false) else null
        val anchor = listOfNotNull(smsAnchor, manualAnchor).maxByOrNull { it.second } ?: return null
        val (base, at, fromSms) = anchor
        val later = payments.filter {
            it.status == PaymentStatus.SUCCESS && !it.isCash && it.createdAt > at &&
                (!fromSms || it.smsEventId != latestSmsWithBalance?.id)
        }
        val delta = later.sumOf { if (it.direction == Direction.IN) it.amountPaise else -it.amountPaise }
        val lastChange = later.maxOfOrNull { it.createdAt } ?: at
        return BalanceInfo(base + delta, lastChange, isEstimate = later.isNotEmpty(), fromSms = fromSms)
    }
}
