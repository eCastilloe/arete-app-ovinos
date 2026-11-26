package com.arete.app.data.repository

import com.arete.app.data.model.InventorySnapshot

/** Supplies a complete, authorized ranch snapshot. HU-17/offline sync will provide it. */
fun interface InventoryRepository {
    suspend fun load(): InventorySnapshot
}

class InventoryUnavailableException : Exception()

class UnavailableInventoryRepository : InventoryRepository {
    override suspend fun load(): InventorySnapshot = throw InventoryUnavailableException()
}
