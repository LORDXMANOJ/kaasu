package com.manoj.kaasu.data.notif

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.db.SmsEventDao
import com.manoj.kaasu.data.sms.Reconciler
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Turns a UPI-app notification into the same event a bank SMS would produce. Raw text is never stored. */
@Singleton
class NotificationIngestor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsEventDao: SmsEventDao,
    private val reconciler: Reconciler,
) {
    suspend fun ingest(packageName: String, title: String, text: String, postedAt: Long) {
        val parsed = NotificationParser.parse(title, text)
        val app = appName(packageName)
        if (parsed == null) {
            NotifStatus.seen(context, "Skipped a $app alert: ${NotificationParser.whySkipped(title, text)}")
            return
        }
        val summary = (if (parsed.direction == Direction.IN) "Received " else "Paid ") +
            Money.format(parsed.amountPaise) + (parsed.counterparty?.let { " · $it" } ?: "")
        NotifStatus.seen(context, "$app: $summary")
        val event = SmsEvent(
            providerId = syntheticId(packageName, postedAt, title, text),
            sender = Reconciler.NOTIFICATION_SENDER_PREFIX + packageName,
            body = summary,
            receivedAt = postedAt,
            direction = parsed.direction,
            amountPaise = parsed.amountPaise,
            counterparty = parsed.counterparty,
        )
        val id = smsEventDao.insert(event)
        if (id == -1L) return
        reconciler.onSmsEvent(event.copy(id = id))
        NotifStatus.recorded(context)
    }

    /** Negative so it can never collide with a real SMS id; stable so a re-posted notification is ignored. */
    private fun syntheticId(packageName: String, postedAt: Long, title: String, text: String): Long {
        val a = "$packageName|$postedAt|$title".hashCode().toLong()
        val b = "$text|$postedAt".hashCode().toLong()
        return ((a shl 32) xor (b and 0xFFFFFFFFL)) or Long.MIN_VALUE
    }

    private fun appName(packageName: String): String = when (packageName) {
        "net.one97.paytm" -> "Paytm"
        "com.phonepe.app" -> "PhonePe"
        "money.super.payments" -> "super.money"
        "com.google.android.apps.nbu.paisa.user" -> "GPay"
        "in.org.npci.upiapp" -> "BHIM"
        else -> "SBI"
    }

    companion object {
        /** Ask Android to reconnect the reader (it sometimes stays "bound" but idle after an app update). */
        fun requestRebind(context: Context) {
            if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) return
            runCatching { NotificationListenerService.requestRebind(ComponentName(context, UpiNotificationListener::class.java)) }
        }
    }
}

/**
 * Reads ONLY notifications from known UPI apps. Everything else is dropped on the first line,
 * before its text is touched. Kaasu has no internet permission, so nothing can leave the phone.
 */
@AndroidEntryPoint
class UpiNotificationListener : NotificationListenerService() {
    @Inject lateinit var ingestor: NotificationIngestor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Also picks up UPI alerts that were already in the panel before Kaasu started listening. */
    override fun onListenerConnected() {
        val active = runCatching { activeNotifications }
            .onFailure { NotifStatus.seen(this, "Couldn't list alerts: ${it.javaClass.simpleName}") }
            .getOrNull()
            .orEmpty()
        NotifStatus.connected(this, active.count { it.packageName in NotificationParser.UPI_PACKAGES })
        active.forEach(::handle)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName !in NotificationParser.UPI_PACKAGES) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val postedAt = sbn.postTime
        val packageName = sbn.packageName
        val appContext = applicationContext
        scope.launch {
            runCatching { ingestor.ingest(packageName, title, text, postedAt) }
                .onFailure { NotifStatus.seen(appContext, "Error: ${it.javaClass.simpleName} ${it.message.orEmpty().take(80)}") }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
