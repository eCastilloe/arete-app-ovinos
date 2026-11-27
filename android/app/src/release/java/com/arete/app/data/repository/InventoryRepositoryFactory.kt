package com.arete.app.data.repository

import com.arete.app.data.local.TokenStorage
import android.content.Context
import com.arete.app.data.remote.ApiService

object InventoryRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, tokenStorage: TokenStorage, apiService: ApiService, authRepository: AuthRepository): InventoryRepository =
        RemoteInventoryRepository(apiService, authRepository::getRanches)
}

object AnimalEditorRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, tokenStorage: TokenStorage, remote: AnimalEditorRepository): AnimalEditorRepository = remote
}

object AnimalDetailRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, tokens: TokenStorage, remote: AnimalDetailRepository): AnimalDetailRepository = remote
}
