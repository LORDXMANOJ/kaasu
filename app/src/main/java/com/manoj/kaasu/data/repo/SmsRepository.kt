package com.manoj.kaasu.data.repo

import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.db.SmsEventDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(private val dao: SmsEventDao) {
    val latestWithBalance: Flow<SmsEvent?> = dao.observeLatestWithBalance()
    val count: Flow<Int> = dao.observeCount()

    suspend fun getById(id: Long): SmsEvent? = dao.getById(id)
}
