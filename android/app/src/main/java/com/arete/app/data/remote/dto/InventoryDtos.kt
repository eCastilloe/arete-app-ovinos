package com.arete.app.data.remote.dto

import com.arete.app.data.model.*
import com.google.gson.annotations.SerializedName

data class CurrentUserDto(
    @SerializedName("usuario_id") val userId: String,
    @SerializedName("ranchos") val ranches: List<RanchAccessDto>
)

data class RanchAccessDto(
    @SerializedName("rancho_id") val ranchId: String,
    @SerializedName("nombre") val name: String,
    @SerializedName("rol") val role: String,
    @SerializedName("origen") val origin: String,
    @SerializedName("puede_capturar") val canCapture: Boolean,
    @SerializedName("puede_editar") val canEdit: Boolean,
    @SerializedName("puede_exportar") val canExport: Boolean,
    @SerializedName("puede_corregir") val canCorrect: Boolean
)

data class InventoryPageDto(
    @SerializedName("rancho_id") val ranchId: String,
    @SerializedName("rancho_nombre") val ranchName: String,
    @SerializedName("puede_editar") val canEdit: Boolean,
    @SerializedName("total") val total: Int,
    @SerializedName("pagina") val page: Int,
    @SerializedName("tamano") val pageSize: Int,
    @SerializedName("items") val items: List<InventoryAnimalDto>
)

data class InventoryAnimalDto(
    @SerializedName("id") val id: String,
    @SerializedName("rancho_id") val ranchId: String,
    @SerializedName("folio") val folio: String,
    @SerializedName("siniiga") val siniiga: String?,
    @SerializedName("sexo") val sex: String,
    @SerializedName("categoria") val category: String,
    @SerializedName("situacion") val situation: String,
    @SerializedName("raza") val breed: String?,
    @SerializedName("estado_reproductivo") val reproductiveState: String?,
    @SerializedName("ultimo_peso_kg") val lastWeightKg: Double?,
    @SerializedName("es_externo") val isExternal: Boolean
) {
    fun toInventoryAnimal() = InventoryAnimal(
        id = id, ranchId = ranchId, folio = folio, siniiga = siniiga,
        sex = when (sex) {
            "H" -> AnimalSex.FEMALE
            "M" -> AnimalSex.MALE
            else -> error("Unknown animal sex")
        },
        category = when (category) {
            "vientre" -> AnimalCategory.EWE
            "semental" -> AnimalCategory.STUD
            "cria_lactante" -> AnimalCategory.SUCKLING
            "cordero_engorda" -> AnimalCategory.FATTENING
            "cordera_recria" -> AnimalCategory.REPLACEMENT
            else -> error("Unknown animal category")
        },
        situation = when (situation) {
            "activo" -> AnimalSituation.ACTIVE
            "vendido" -> AnimalSituation.SOLD
            "muerto" -> AnimalSituation.DEAD
            "desecho" -> AnimalSituation.CULLED
            "transferido" -> AnimalSituation.TRANSFERRED
            else -> error("Unknown animal situation")
        },
        breed = breed,
        reproductiveState = when (reproductiveState) {
            null -> null
            "vacia" -> "Vacía"
            "empadre" -> "En empadre"
            "gestante" -> "Gestante"
            "lactancia" -> "Lactando"
            else -> error("Unknown reproductive state")
        },
        lastWeightKg = lastWeightKg,
        isExternal = isExternal
    )
}
