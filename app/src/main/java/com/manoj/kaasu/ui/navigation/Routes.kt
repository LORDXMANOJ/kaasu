package com.manoj.kaasu.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val PEOPLE = "people"
    const val REPORTS = "reports"
    const val UNTAGGED = "untagged"
    const val SETTINGS = "settings"
    const val CATEGORIES = "settings/categories"
    const val PICK_PERSON = "pick-person"
    const val PROFILE = "profile"

    const val SCAN = "scan?mode={mode}"
    const val SCAN_MODE_PAY = "pay"
    const val SCAN_MODE_PERSON = "person"
    fun scan(mode: String = SCAN_MODE_PAY) = "scan?mode=$mode"

    const val PAY = "pay?raw={raw}&contactId={contactId}&lend={lend}&amount={amount}"
    fun pay(raw: String? = null, contactId: Long = 0, lend: Boolean = false, amountPaise: Long = 0): String =
        "pay?raw=${Uri.encode(raw.orEmpty())}&contactId=$contactId&lend=$lend&amount=$amountPaise"

    const val TXN = "txn/{id}"
    fun txn(id: Long) = "txn/$id"

    const val PERSON = "person/{id}"
    fun person(id: Long) = "person/$id"

    const val PERSON_EDIT = "person-edit?id={id}&vpa={vpa}&name={name}"
    fun personEdit(id: Long = 0, vpa: String? = null, name: String? = null) =
        "person-edit?id=$id&vpa=${Uri.encode(vpa.orEmpty())}&name=${Uri.encode(name.orEmpty())}"

    const val REPORT_SHARE = "report-share?week={week}"
    fun reportShare(weekStartEpochDay: Long) = "report-share?week=$weekStartEpochDay"

    /** Result keys passed back through SavedStateHandle. */
    const val RESULT_SCANNED_VPA = "scanned_vpa"
    const val RESULT_SCANNED_NAME = "scanned_name"

    val topLevel = setOf(HOME, HISTORY, PEOPLE, REPORTS)
}
