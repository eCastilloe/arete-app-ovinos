package com.arete.app.data.repository

import com.arete.app.data.local.TokenStorage
import android.content.Context
import com.arete.app.data.remote.ApiService

object InventoryRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, tokenStorage: TokenStorage, apiService: ApiService): InventoryRepository =
        RemoteInventoryRepository(apiService)
}

object AnimalEditorRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, tokenStorage: TokenStorage): AnimalEditorRepository = UnavailableAnimalEditorRepository()
}
