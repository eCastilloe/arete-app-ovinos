package com.arete.app.data.repository

import com.arete.app.data.model.*

interface AnimalEditorRepository {
    suspend fun loadEditor(animalId: String?): AnimalEditorContext
    suspend fun save(animalId: String?, form: AnimalForm, expectedVersion: Int?): AnimalRecord
}

class AnimalValidationException(val fields: Map<AnimalField, String>) : Exception()
class AnimalEditingDeniedException : Exception()
class AnimalEditConflictException : Exception()

class UnavailableAnimalEditorRepository : AnimalEditorRepository {
    override suspend fun loadEditor(animalId: String?): AnimalEditorContext = throw InventoryUnavailableException()
    override suspend fun save(animalId: String?, form: AnimalForm, expectedVersion: Int?): AnimalRecord = throw AnimalEditingDeniedException()
}
