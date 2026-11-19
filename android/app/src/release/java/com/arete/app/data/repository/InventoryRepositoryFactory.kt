package com.arete.app.data.repository

import com.arete.app.data.local.TokenStorage

object InventoryRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(tokenStorage: TokenStorage): InventoryRepository = UnavailableInventoryRepository()
}
