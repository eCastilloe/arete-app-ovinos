package com.arete.app.data.model

enum class AnimalSex { FEMALE, MALE }

enum class InventoryCategory(val label: String) {
    FEMALES("Hembras"), STUDS("Sementales"), YOUNG("Crías")
}

// These are the five categories in docs/03. Young females are not adult ewes.
enum class AnimalCategory(val section: InventoryCategory, val label: String) {
    EWE(InventoryCategory.FEMALES, "Vientre"),
    STUD(InventoryCategory.STUDS, "Semental"),
    SUCKLING(InventoryCategory.YOUNG, "Cría lactante"),
    FATTENING(InventoryCategory.YOUNG, "Cordero en engorda"),
    REPLACEMENT(InventoryCategory.YOUNG, "Cordera en recría")
}

enum class AnimalSituation(val label: String) {
    ACTIVE("Activo"), SOLD("Vendido"), DEAD("Muerto"),
    CULLED("Desecho"), TRANSFERRED("Transferido")
}

data class InventoryAnimal(
    val id: String,
    val ranchId: String,
    val folio: String,
    val siniiga: String? = null,
    val sex: AnimalSex,
    val category: AnimalCategory,
    val situation: AnimalSituation = AnimalSituation.ACTIVE,
    val breed: String?,
    val reproductiveState: String? = null,
    val lastWeightKg: Double? = null,
    val isExternal: Boolean = false,
    val isDeleted: Boolean = false
)

data class InventorySnapshot(
    val ranchId: String,
    val ranchName: String,
    val animals: List<InventoryAnimal>,
    val isDemo: Boolean = false,
    val canEdit: Boolean = false
)
