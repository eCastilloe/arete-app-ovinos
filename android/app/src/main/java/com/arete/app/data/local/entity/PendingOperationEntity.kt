package com.arete.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val ranchId: String,
    val userId: String,
    val entityName: String,
    val entityId: String,
    val operationType: String,
    val payload: String,
    val baseVersion: Int? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SYNCED = "synced"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_CONFLICT = "conflict"
    }
}