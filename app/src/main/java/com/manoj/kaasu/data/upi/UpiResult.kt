package com.manoj.kaasu.data.upi

import com.manoj.kaasu.data.db.PaymentStatus

/** What the UPI app told us on return. Many apps return little or nothing; SMS fills the gaps. */
data class UpiResult(
    val status: PaymentStatus?,
    val txnId: String?,
    val approvalRef: String?,
    val responseCode: String?,
    val raw: String?,
)

object UpiResponseParser {
    fun parse(raw: String?): UpiResult {
        if (raw.isNullOrBlank()) return UpiResult(null, null, null, null, raw)
        val map = raw.split('&', '\n')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "").trim().lowercase()
                if (key.isEmpty()) null else key to part.substringAfter('=').trim()
            }
            .toMap()
        val statusText = map["status"]?.lowercase()
        val code = map["responsecode"]?.takeIf { it.isNotBlank() && it != "null" }
        val status = when {
            statusText == null -> null
            statusText.startsWith("success") -> PaymentStatus.SUCCESS
            statusText.startsWith("fail") -> PaymentStatus.FAILED
            statusText.startsWith("submit") || statusText.startsWith("pending") -> PaymentStatus.PENDING
            else -> null
        }
        return UpiResult(
            status = status,
            txnId = map["txnid"]?.cleanRef(),
            approvalRef = (map["approvalrefno"] ?: map["approvalref"])?.cleanRef(),
            responseCode = code,
            raw = raw,
        )
    }

    private fun String.cleanRef(): String? = trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}
