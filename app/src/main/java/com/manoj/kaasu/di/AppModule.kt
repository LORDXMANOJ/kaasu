package com.manoj.kaasu.di

import android.content.Context
import androidx.room.Room
import com.manoj.kaasu.data.db.CategoryDao
import com.manoj.kaasu.data.db.ContactDao
import com.manoj.kaasu.data.db.KaasuDatabase
import com.manoj.kaasu.data.db.MealLogDao
import com.manoj.kaasu.data.db.PaymentDao
import com.manoj.kaasu.data.db.SmsEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

/** Long-lived scope for work that must outlive a screen (e.g. saving after the UPI app returns). */
@Singleton
class AppScope @Inject constructor() : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): KaasuDatabase =
        Room.databaseBuilder(context, KaasuDatabase::class.java, KaasuDatabase.NAME).build()

    @Provides fun paymentDao(db: KaasuDatabase): PaymentDao = db.paymentDao()
    @Provides fun categoryDao(db: KaasuDatabase): CategoryDao = db.categoryDao()
    @Provides fun contactDao(db: KaasuDatabase): ContactDao = db.contactDao()
    @Provides fun mealLogDao(db: KaasuDatabase): MealLogDao = db.mealLogDao()
    @Provides fun smsEventDao(db: KaasuDatabase): SmsEventDao = db.smsEventDao()
}
