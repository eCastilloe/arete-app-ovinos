package com.arete.app.data.repository

import com.arete.app.data.model.*

fun interface AnimalDetailRepository {
    suspend fun loadDetail(animalId: String): AnimalDetail
}

class AnimalDetailNotFoundException : Exception()

/** Read-only adapter over the existing authorized local snapshot; never calls save. */
class LocalAnimalDetailRepository(private val source: AnimalEditorRepository) : AnimalDetailRepository {
    override suspend fun loadDetail(animalId: String): AnimalDetail {
        if (animalId.isBlank()) throw AnimalDetailNotFoundException()
        val context = source.loadEditor(animalId)
        val animal = context.existing?.takeIf {
            it.id == animalId && it.ranchId == context.ranchId && !it.isDeleted && !it.isExternal
        } ?: throw AnimalDetailNotFoundException()
        fun parentLabel(parent: ParentChoice, sex: AnimalSex) = when (parent.kind) {
            ParentKind.UNKNOWN -> if (sex == AnimalSex.FEMALE) "Desconocida" else "Desconocido"
            ParentKind.EXTERNAL -> (if (sex == AnimalSex.FEMALE) "Externa" else "Externo") +
                parent.reference.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            ParentKind.LOCAL -> context.records.firstOrNull {
                it.id == parent.id && it.ranchId == context.ranchId && it.form.sex == sex && !it.isDeleted && !it.isExternal
            }?.form?.folio ?: "No disponible"
        }
        return AnimalDetail(animal, context.ranchName,
            parentLabel(animal.form.mother, AnimalSex.FEMALE), parentLabel(animal.form.father, AnimalSex.MALE),
            context.canEdit, context.isDemo)
    }
}
