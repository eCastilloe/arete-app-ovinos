package com.arete.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arete.app.data.local.dao.PendingOperationDao
import com.arete.app.data.local.entity.PendingOperationEntity

@Database(
    entities = [PendingOperationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingOperationDao(): PendingOperationDao
}