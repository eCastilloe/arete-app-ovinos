package com.arete.app.data.repository

import com.arete.app.data.local.dao.PendingOperationDao
import kotlinx.coroutines.flow.Flow

class SyncRepository(private val pendingOperationDao: PendingOperationDao) {

    val pendingCount: Flow<Int> = pendingOperationDao.observePendingCount()
}