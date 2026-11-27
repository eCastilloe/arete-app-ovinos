package com.arete.app.data.repository

import com.arete.app.data.model.InventorySnapshot
import com.arete.app.data.remote.ApiService
import com.arete.app.data.remote.dto.RanchAccessDto
import retrofit2.HttpException

class NoAssignedRanchException : Exception()
class IncompleteInventoryException : Exception()

/** HU-18: selects the first authorized ranch; multi-ranch selection belongs to HU-14. */
class RemoteInventoryRepository(
    private val apiService: ApiService,
    private val ranches: suspend () -> List<RanchAccessDto> = {
        val userResponse = apiService.getCurrentUser()
        if (!userResponse.isSuccessful) throw HttpException(userResponse)
        requireNotNull(userResponse.body()).ranches
    }
) : InventoryRepository {
    override suspend fun load(): InventorySnapshot {
        val ranch = ranches().firstOrNull() ?: throw NoAssignedRanchException()
        val page = apiService.getInventory(ranch.ranchId)
        // Do not publish partial/wrong-ranch counts as a complete inventory.
        check(page.ranchId == ranch.ranchId)
        check(page.items.all { it.ranchId == ranch.ranchId })
        if (page.page != 1 || page.total != page.items.size || page.total > 1000) {
            throw IncompleteInventoryException()
        }
        return InventorySnapshot(
            ranchId = ranch.ranchId,
            ranchName = page.ranchName,
            animals = page.items.map { it.toInventoryAnimal() },
            canEdit = ranch.canEdit && page.canEdit
        )
    }
}
