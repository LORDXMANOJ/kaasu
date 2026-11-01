package com.manoj.kaasu.data.upi

import com.manoj.kaasu.core.Money
import java.net.URLDecoder
import java.net.URLEncoder

/** What we understood from a scanned QR / UPI link. Pure Kotlin so it is unit-testable. */
data class UpiPayload(
    val vpa: String,
    val name: String?,
    val amountPaise: Long?,
    val note: String?,
    val merchantCode: String?,
    val isSigned: Boolean,
    val params: List<Pair<String, String>>,
    /** Normalised original link; reused as-is so shop QRs keep their exact parameters. */
    val raw: String,
) {
    val isMerchant: Boolean get() = !merchantCode.isNullOrBlank() && merchantCode != "0000"

    /** Signed QRs with a fixed amount cannot be edited without breaking the signature. */
    val isAmountLocked: Boolean get() = isSigned && (amountPaise ?: 0) > 0
}

object UpiUri {
    private const val PREFIX = "upi://pay"
    private val VPA = Regex("^[a-zA-Z0-9._\\-]{2,256}@[a-zA-Z][a-zA-Z0-9.\\-]{1,64}$")
    private val VPA_ANYWHERE = Regex("[a-zA-Z0-9._\\-]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,64}")
    private val NOTE_ALLOWED = Regex("[^A-Za-z0-9 ,.\\-]")
    private const val MAX_NOTE = 50

    fun isValidVpa(value: String): Boolean = VPA.matches(value.trim())

    fun parse(content: String?): UpiPayload? {
        val text = content?.trim().orEmpty()
        if (text.isEmpty()) return null
        return when {
            text.startsWith(PREFIX, ignoreCase = true) -> parseUpiLink(text)
            text.startsWith("000201") -> parseEmv(text)
            isValidVpa(text) -> fromVpa(text, null)
            else -> null
        }
    }

    fun fromVpa(vpa: String, name: String?): UpiPayload {
        val params = buildList {
            add("pa" to vpa.trim())
            safePayeeName(name)?.let { add("pn" to it) }
        }
        return UpiPayload(vpa.trim(), name?.trim(), null, null, null, false, params, buildLink(params))
    }

    private fun parseUpiLink(text: String): UpiPayload? {
        val normalised = PREFIX + text.substring(PREFIX.length)
        val query = normalised.substringAfter('?', "")
        if (query.isEmpty()) return null
        val params = query.split('&').filter { it.isNotEmpty() }.map { part ->
            val key = part.substringBefore('=').trim()
            val value = decode(part.substringAfter('=', ""))
            key to value
        }
        val map = params.associate { it.first.lowercase() to it.second }
        val vpa = map["pa"]?.trim()?.takeIf { isValidVpa(it) } ?: return null
        return UpiPayload(
            vpa = vpa,
            name = map["pn"]?.trim()?.takeIf { it.isNotEmpty() },
            amountPaise = map["am"]?.let { Money.parseToPaise(it) }?.takeIf { it > 0 },
            note = map["tn"]?.trim()?.takeIf { it.isNotEmpty() },
            merchantCode = map["mc"]?.trim()?.takeIf { it.isNotEmpty() },
            isSigned = !map["sign"].isNullOrBlank(),
            params = params,
            raw = normalised,
        )
    }

    /** BharatQR / EMVCo merchant QR: TLV encoded, UPI handle inside merchant account templates 26..51. */
    private fun parseEmv(text: String): UpiPayload? {
        val top = parseTlv(text) ?: return null
        val vpa = (26..51).asSequence()
            .mapNotNull { top[it.toString().padStart(2, '0')] }
            .flatMap { template -> (parseTlv(template)?.values ?: emptyList()).asSequence() + sequenceOf(template) }
            .mapNotNull { VPA_ANYWHERE.find(it)?.value }
            .firstOrNull() ?: return null
        val name = top["59"]?.trim()
        val amount = top["54"]?.let { Money.parseToPaise(it) }
        val params = buildList {
            add("pa" to vpa)
            if (!name.isNullOrBlank()) add("pn" to name)
            top["52"]?.let { add("mc" to it) }
            if (amount != null) add("am" to Money.toUpiAmount(amount))
        }
        return UpiPayload(vpa, name, amount, null, top["52"], false, params, buildLink(params))
    }

    private fun parseTlv(data: String): Map<String, String>? {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i + 4 <= data.length) {
            val tag = data.substring(i, i + 2)
            val length = data.substring(i + 2, i + 4).toIntOrNull() ?: return null
            val end = i + 4 + length
            if (end > data.length) return null
            out[tag] = data.substring(i + 4, end)
            i = end
        }
        return if (out.isEmpty()) null else out
    }

    /**
     * Builds the link handed to the UPI app. For scanned QRs we change as little as possible
     * (only the amount, and a note when the QR is unsigned) because shop QRs often break otherwise.
     */
    fun buildPayLink(payload: UpiPayload, amountPaise: Long, note: String?): String {
        if (payload.isAmountLocked) return payload.raw
        var link = removeParam(payload.raw, "am")
        link = appendParam(link, "am", Money.toUpiAmount(amountPaise))
        val keys = payload.params.map { it.first.lowercase() }.toSet()
        if ("cu" !in keys) link = appendParam(link, "cu", "INR")
        val cleanNote = sanitizeNote(note)
        if (!payload.isSigned && "tn" !in keys && cleanNote != null) link = appendParam(link, "tn", cleanNote)
        return link
    }

    /** Link for paying a saved person by their UPI ID. Kept minimal on purpose (P2P). */
    fun buildPersonLink(vpa: String, name: String?, amountPaise: Long, note: String?): String {
        val params = buildList {
            add("pa" to vpa.trim())
            safePayeeName(name)?.let { add("pn" to it) }
            add("am" to Money.toUpiAmount(amountPaise))
            add("cu" to "INR")
            sanitizeNote(note)?.let { add("tn" to it) }
        }
        return buildLink(params)
    }

    /**
     * NPCI "UPI Number": any Indian mobile number linked to UPI can be paid as "<10 digits>@upi".
     * The UPI app shows the receiver's registered bank name before the PIN, so the user can check it.
     */
    fun upiNumberVpa(phone: String?): String? {
        var digits = phone?.filter { it.isDigit() }.orEmpty()
        if (digits.length == 12 && digits.startsWith("91")) digits = digits.drop(2)
        if (digits.length == 11 && digits.startsWith("0")) digits = digits.drop(1)
        return if (digits.length == 10 && digits[0] in '6'..'9') "$digits@upi" else null
    }

    /** UPI apps can reject a payee name with emoji or symbols (for example a name with an emoji), so only plain letters, digits and spaces go out. */
    fun safePayeeName(name: String?): String? =
        name?.replace(Regex("[^A-Za-z0-9 .]"), " ")?.replace(Regex("\\s+"), " ")?.trim()?.take(40)?.takeIf { it.length >= 2 }

    fun sanitizeNote(note: String?): String? =
        note?.replace(NOTE_ALLOWED, " ")?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_NOTE)?.takeIf { it.isNotEmpty() }

    private fun buildLink(params: List<Pair<String, String>>): String =
        PREFIX + "?" + params.joinToString("&") { (k, v) -> "$k=${encode(v)}" }

    private fun removeParam(link: String, key: String): String {
        val base = link.substringBefore('?')
        val query = link.substringAfter('?', "")
        val kept = query.split('&').filter { it.isNotEmpty() && !it.substringBefore('=').equals(key, ignoreCase = true) }
        return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
    }

    private fun appendParam(link: String, key: String, value: String): String {
        val separator = if (link.contains('?')) "&" else "?"
        return "$link$separator$key=${encode(value)}"
    }

    /** Percent-encodes a value but keeps '@' readable (allowed in a query and expected in pa=name@bank). */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("%40", "@")

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
