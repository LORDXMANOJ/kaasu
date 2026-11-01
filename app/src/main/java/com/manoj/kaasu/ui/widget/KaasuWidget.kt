package com.manoj.kaasu.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.manoj.kaasu.MainActivity
import com.manoj.kaasu.R
import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.core.Money
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.repo.PaymentRepository
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.di.AppScope
import com.manoj.kaasu.domain.BudgetRules
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Computes "₹ left today" and pushes it to every Kaasu widget on the home screen. Shows only an amount, never names. */
@Singleton
class KaasuWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val payments: PaymentRepository,
    private val categories: CategoryRepository,
    private val appScope: AppScope,
) {
    /** Keeps widgets current while the app process is alive (after payments, SMS, notifications). */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        appScope.launch {
            combine(settings.settings, payments.all, categories.categories) { _, _, _ -> Unit }
                .debounce(500)
                .collect { refresh() }
        }
    }

    suspend fun refresh() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, KaasuWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val s = settings.current()
        val today = Dates.today()
        val cats = categories.getAll().associateBy { it.id }
        val spent = BudgetRules.spentOn(today, payments.getRange(today, today), cats, s.countLentInBudget)
        val left = s.budgetForDay(today.toEpochDay()) - spent
        val views = RemoteViews(context.packageName, R.layout.widget_kaasu).apply {
            setTextViewText(R.id.widget_left, Money.format(if (left < 0) -left else left))
            setTextViewText(R.id.widget_caption, if (left < 0) "over budget" else "left today")
            setTextColor(R.id.widget_caption, if (left < 0) 0xFFFCA5A5.toInt() else 0xFFC7D2FE.toInt())
            setOnClickPendingIntent(R.id.widget_budget, open(null, 0))
            setOnClickPendingIntent(R.id.widget_scan, open(MainActivity.OPEN_SCAN, 1))
            setOnClickPendingIntent(R.id.widget_people, open(MainActivity.OPEN_PEOPLE, 2))
        }
        manager.updateAppWidget(ids, views)
    }

    private fun open(target: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (target != null) intent.putExtra(MainActivity.EXTRA_OPEN, target)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

@AndroidEntryPoint
class KaasuWidgetProvider : AppWidgetProvider() {
    @Inject lateinit var updater: KaasuWidgetUpdater
    @Inject lateinit var appScope: AppScope

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        appScope.launch {
            try {
                updater.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
