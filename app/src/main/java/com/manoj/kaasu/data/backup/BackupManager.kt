package com.manoj.kaasu.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.db.KaasuDatabase
import com.manoj.kaasu.data.db.MealLog
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.prefs.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val version: Int = 1,
    val exportedAt: Long,
    val settings: UserSettings,
    val categories: List<Category>,
    val contacts: List<Contact>,
    val payments: List<Payment>,
    val meals: List<MealLog>,
    val smsEvents: List<SmsEvent>,
) {
    companion object {
        const val APP_ID = "kaasu"
    }
}

data class BackupSummary(val payments: Int, val contacts: Int)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: KaasuDatabase,
    private val settings: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val file = BackupFile(
            exportedAt = Dates.now(),
            // The PIN never leaves the phone.
            settings = settings.current().copy(pinHash = null, pinSalt = null, appLockEnabled = false, biometricEnabled = false),
            categories = db.categoryDao().getAll(),
            contacts = db.contactDao().getAll(),
            payments = db.paymentDao().getAll(),
            meals = db.mealLogDao().getAll(),
            smsEvents = db.smsEventDao().getAll(),
        )
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the file")
        stream.use { it.write(json.encodeToString(BackupFile.serializer(), file).toByteArray()) }
        BackupSummary(file.payments.size, file.contacts.size)
    }

    suspend fun restore(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Could not open the file")
        val text = stream.use { it.readBytes().decodeToString() }
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { error("This is not a Kaasu backup file") }
        require(file.app == BackupFile.APP_ID) { "This is not a Kaasu backup file" }
        db.withTransaction {
            db.paymentDao().deleteAll()
            db.categoryDao().deleteAll()
            db.contactDao().deleteAll()
            db.mealLogDao().deleteAll()
            db.smsEventDao().deleteAll()
            db.categoryDao().insertAll(file.categories)
            db.contactDao().insertAll(file.contacts)
            db.paymentDao().insertAll(file.payments)
            db.mealLogDao().insertAll(file.meals)
            db.smsEventDao().insertAll(file.smsEvents)
        }
        settings.restore(file.settings)
        BackupSummary(file.payments.size, file.contacts.size)
    }

    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val categories = db.categoryDao().getAll().associateBy { it.id }
        val contacts = db.contactDao().getAll().associateBy { it.id }
        val payments = db.paymentDao().getAll()
        val header = "Date,Time,Type,Amount,Payee,UPI ID,Category,Person,Note,Status,Source,Our Ref,UPI Txn ID,Bank Ref (UTR)"
        val rows = payments.map { p ->
            val dt = Dates.toLocalDateTime(p.createdAt)
            listOf(
                dt.toLocalDate().toString(),
                dt.toLocalTime().withNano(0).toString(),
                if (p.direction.name == "OUT") "Paid" else "Received",
                Money.format(p.amountPaise, withSymbol = false, forceDecimals = true).replace(",", ""),
                p.payeeName.orEmpty(),
                p.payeeVpa.orEmpty(),
                categories[p.categoryId]?.name ?: "Untagged",
                contacts[p.contactId]?.name.orEmpty(),
                p.note.orEmpty(),
                p.status.name,
                p.source.name,
                p.txnRef,
                p.upiTxnId.orEmpty(),
                p.bankRef.orEmpty(),
            ).joinToString(",") { csv(it) }
        }
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the file")
        stream.use { it.write((listOf(header) + rows).joinToString("\n").toByteArray()) }
        payments.size
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
