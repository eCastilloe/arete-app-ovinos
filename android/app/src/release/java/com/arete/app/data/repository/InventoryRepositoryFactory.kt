package com.arete.app.data.repository

import com.arete.app.data.local.TokenStorage
import android.content.Context

object InventoryRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, tokenStorage: TokenStorage): InventoryRepository = UnavailableInventoryRepository()
}

object AnimalEditorRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, tokenStorage: TokenStorage): AnimalEditorRepository = UnavailableAnimalEditorRepository()
}
