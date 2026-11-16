package com.arete.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.arete.app.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOperationDao {

    @Insert
    suspend fun insert(operation: PendingOperationEntity)

    @Query("SELECT * FROM pending_operations WHERE status = 'pending' ORDER BY capturedAt ASC")
    suspend fun getPending(): List<PendingOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE pending_operations SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}