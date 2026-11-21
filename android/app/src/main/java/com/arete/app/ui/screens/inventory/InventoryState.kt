package com.arete.app.ui.screens.inventory

import com.arete.app.data.model.*

data class InventoryFilters(
    val category: InventoryCategory = InventoryCategory.FEMALES,
    val query: String = "",
    val showDischarged: Boolean = false
)

data class InventoryUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val ranchName: String = "Inventario",
    val isDemo: Boolean = false,
    val canEdit: Boolean = false,
    val filters: InventoryFilters = InventoryFilters(),
    val counts: Map<InventoryCategory, Int> = emptyMap(),
    val dischargedCount: Int = 0,
    val animals: List<InventoryAnimal> = emptyList(),
    val averageWeightKg: Double? = null,
    val weighedCount: Int = 0
)

/** Pure calculation, shared by the screen and tests; no network or UI calls. */
fun projectInventory(snapshot: InventorySnapshot, filters: InventoryFilters): InventoryUiState {
    // Defense in depth, not a replacement for the server's ranch permissions.
    val local = snapshot.animals.filter {
        it.ranchId == snapshot.ranchId && !it.isExternal && !it.isDeleted
    }
    val active = local.filter { it.situation == AnimalSituation.ACTIVE }
    val discharged = local.filter { it.situation != AnimalSituation.ACTIVE }
    val query = filters.query.trim()
    val selected = (if (filters.showDischarged) discharged else active).filter {
        if (query.isNotEmpty()) {
            it.folio.contains(query, ignoreCase = true) || it.siniiga?.contains(query) == true
        } else filters.showDischarged || it.category.section == filters.category
    }
    val weights = selected.mapNotNull { it.lastWeightKg?.takeIf { kg -> kg.isFinite() && kg > 0 } }
    return InventoryUiState(
        isLoading = false,
        ranchName = snapshot.ranchName,
        isDemo = snapshot.isDemo,
        canEdit = snapshot.canEdit,
        filters = filters,
        counts = InventoryCategory.entries.associateWith { section -> active.count { it.category.section == section } },
        dischargedCount = discharged.size,
        animals = selected,
        averageWeightKg = weights.takeIf { it.isNotEmpty() }?.average(),
        weighedCount = weights.size
    )
}
