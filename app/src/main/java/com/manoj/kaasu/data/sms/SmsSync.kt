package com.manoj.kaasu.data.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.db.SmsEventDao
import com.manoj.kaasu.data.prefs.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RawSms(val providerId: Long, val sender: String, val body: String, val date: Long)

@Singleton
class SmsInboxReader @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun readSince(since: Long): List<RawSms> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val out = ArrayList<RawSms>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                out += RawSms(
                    providerId = cursor.getLong(idCol),
                    sender = cursor.getString(addressCol).orEmpty(),
                    body = cursor.getString(bodyCol).orEmpty(),
                    date = cursor.getLong(dateCol),
                )
            }
        }
        return out
    }
}

@Singleton
class SmsSyncManager @Inject constructor(
    private val reader: SmsInboxReader,
    private val smsEventDao: SmsEventDao,
    private val reconciler: Reconciler,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    /** Returns the number of new bank transactions found. */
    suspend fun sync(lookBackDays: Int? = null): Int = mutex.withLock {
        val current = settings.current()
        var found = 0
        if (current.smsSyncEnabled && reader.hasPermission()) {
            val now = Dates.now()
            val since = when {
                lookBackDays != null -> now - lookBackDays * DAY_MS
                current.lastSmsScanAt == 0L -> now - FIRST_SYNC_DAYS * DAY_MS
                else -> current.lastSmsScanAt - OVERLAP_MS
            }
            reader.readSince(since).forEach { raw -> if (process(raw)) found++ }
            settings.setLastSmsScan(now)
        }
        reconciler.sweepStale()
        found
    }

    private suspend fun process(raw: RawSms): Boolean {
        val parsed = SmsParser.parse(raw.sender, raw.body, raw.date) ?: return processBalanceOnly(raw)
        val event = SmsEvent(
            providerId = raw.providerId,
            sender = raw.sender,
            body = raw.body,
            receivedAt = raw.date,
            direction = parsed.direction,
            amountPaise = parsed.amountPaise,
            balancePaise = parsed.balancePaise,
            refNo = parsed.refNo,
            counterparty = parsed.counterparty,
            account = parsed.account,
        )
        val id = smsEventDao.insert(event)
        if (id == -1L) return false
        reconciler.onSmsEvent(event.copy(id = id))
        return true
    }

    /** Balance enquiry replies only set the balance; they are not payments. */
    private suspend fun processBalanceOnly(raw: RawSms): Boolean {
        val balance = SmsParser.parseBalanceOnly(raw.sender, raw.body) ?: return false
        val event = SmsEvent(
            providerId = raw.providerId,
            sender = raw.sender,
            body = raw.body,
            receivedAt = raw.date,
            direction = com.manoj.kaasu.data.db.Direction.IN,
            amountPaise = 0,
            balancePaise = balance,
        )
        return smsEventDao.insert(event) != -1L
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60_000L
        private const val FIRST_SYNC_DAYS = 30L
        private const val OVERLAP_MS = 10 * 60_000L
    }
}

@HiltWorker
class SmsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SmsSyncManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { syncManager.sync() }
        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SmsSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.NONE)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun syncSoon(delaySeconds: Long = 0) {
        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        private const val PERIODIC = "sms-sync-periodic"
        private const val ONE_SHOT = "sms-sync-now"
    }
}

/** A new SMS arrived: read it from the inbox a few seconds later (after the system has stored it). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        SyncScheduler(context.applicationContext).syncSoon(delaySeconds = 4)
    }
}
