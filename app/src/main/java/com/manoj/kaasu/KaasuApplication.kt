package com.manoj.kaasu

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.manoj.kaasu.data.repo.CategoryRepository
import com.manoj.kaasu.data.sms.SyncScheduler
import com.manoj.kaasu.di.AppScope
import com.manoj.kaasu.security.AppLockManager
import com.manoj.kaasu.ui.widget.KaasuWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KaasuApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var appScope: AppScope
    @Inject lateinit var widgetUpdater: KaasuWidgetUpdater

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        lockManager.start()
        appScope.launch { categoryRepository.ensureSeeded() }
        syncScheduler.schedulePeriodic()
        widgetUpdater.start()
    }
}
