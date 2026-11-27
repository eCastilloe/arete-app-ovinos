package com.arete.app.data.repository

import com.arete.app.data.model.AnimalDetail
import com.arete.app.data.remote.AnimalApiService
import com.arete.app.data.remote.dto.RanchAccessDto
import retrofit2.HttpException

/** Reads one animal in the active authorized ranch, including read-only users. */
class RemoteAnimalDetailRepository(
    private val api: AnimalApiService,
    private val authorizedRanches: suspend () -> List<RanchAccessDto>,
    private val readPhoto: (String, String) -> String? = { _, _ -> null }
) : AnimalDetailRepository {
    override suspend fun loadDetail(animalId: String): AnimalDetail {
        if (animalId.isBlank()) throw AnimalDetailNotFoundException()
        val ranch = authorizedRanches().firstOrNull() ?: throw AnimalDetailNotFoundException()
        val dto = try { api.animal(animalId) } catch (e: HttpException) {
            if (e.code() == 403 || e.code() == 404) throw AnimalDetailNotFoundException()
            throw e
        }
        if (dto.id != animalId || dto.ranchId != ranch.ranchId || dto.isExternal) throw AnimalDetailNotFoundException()
        val record = dto.toRecord()
        fun parent(id: String?, folio: String?, external: Boolean, female: Boolean): String = when {
            id == null -> if (female) "Desconocida" else "Desconocido"
            external -> (if (female) "Externa" else "Externo") + folio?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            else -> folio?.takeIf { it.isNotBlank() } ?: "No disponible"
        }
        return AnimalDetail(record.copy(form = record.form.copy(photoPath = readPhoto(ranch.ranchId, animalId))),
            ranch.name, parent(dto.motherId, dto.motherFolio, dto.motherExternal, true),
            parent(dto.fatherId, dto.fatherFolio, dto.fatherExternal, false), ranch.canEdit, false,
            birthWeightKg = dto.birthWeightKg, circumferenceDate = dto.circumferenceDate,
            entryReasonLabel = if (dto.reason == "nacimiento") "Nacimiento" else record.form.reason?.label.orEmpty())
    }
}
