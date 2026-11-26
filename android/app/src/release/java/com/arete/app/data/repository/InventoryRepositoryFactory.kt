package com.arete.app.data.repository

import com.arete.app.data.local.TokenStorage
import com.arete.app.data.remote.ApiService

object InventoryRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(tokenStorage: TokenStorage, apiService: ApiService): InventoryRepository = RemoteInventoryRepository(apiService)
}
