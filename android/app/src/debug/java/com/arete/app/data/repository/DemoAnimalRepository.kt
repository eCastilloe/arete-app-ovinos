package com.arete.app.data.repository

import com.arete.app.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Private, persistent demo data. No HTTP requests and no real ranch writes. */
class DemoAnimalRepository(
    private val file: File,
    private val isDemoSession: suspend () -> Boolean,
    private val hasEditPermission: suspend () -> Boolean = { true }
) : AnimalEditorRepository, InventoryRepository {
    private val gson = Gson()

    override suspend fun load(): InventorySnapshot = mutex.withLock {
        authorize()
        InventorySnapshot(DemoInventoryData.RANCH_ID, "Rancho de ejemplo", read().map { it.toInventory() },
            isDemo = true, canEdit = hasEditPermission())
    }

    override suspend fun loadEditor(animalId: String?): AnimalEditorContext = mutex.withLock {
        authorize()
        context(read(), animalId)
    }

    override suspend fun save(animalId: String?, form: AnimalForm, expectedVersion: Int?): AnimalRecord = mutex.withLock {
        authorize()
        if (!hasEditPermission()) throw AnimalEditingDeniedException()
        val records = read()
        val context = context(records, animalId)
        if (animalId != null && context.existing?.version != expectedVersion) throw AnimalEditConflictException()
        val normalized = form.normalized()
        val errors = validateAnimalForm(normalized, context, LocalDate.now(ZoneId.of("America/Mexico_City"))).toMutableMap()
        val others = records.filter { it.id != animalId && !it.isDeleted }
        if (others.any { !it.isExternal && it.ranchId == context.ranchId && it.form.folio.equals(normalized.folio, true) })
            errors[AnimalField.FOLIO] = "Ese folio ya existe en este rancho. Escribe otro."
        if (normalized.siniiga.isNotEmpty() && others.any { it.form.siniiga == normalized.siniiga })
            errors[AnimalField.SINIIGA] = "Ese SINIIGA ya está registrado."
        if (normalized.registration.isNotEmpty() && others.any { it.form.registration == normalized.registration })
            errors[AnimalField.REGISTRATION] = "Ese registro UNO ya está registrado."
        if (errors.isNotEmpty()) throw AnimalValidationException(errors)
        val old = context.existing
        val result = old?.copy(form = normalized, version = old.version + 1)
            ?: AnimalRecord(UUID.randomUUID().toString(), context.ranchId, normalized)
        val updated = if (old == null) records + result else records.map { if (it.id == old.id) result else it }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(gson.toJson(updated))
        // Same-directory atomic replacement keeps a failed write from erasing a valid fixture.
        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        result
    }

    private suspend fun authorize() {
        if (!isDemoSession()) throw InventoryUnavailableException()
    }

    private suspend fun context(records: List<AnimalRecord>, animalId: String?): AnimalEditorContext {
        val local = records.filter { it.ranchId == DemoInventoryData.RANCH_ID && !it.isDeleted && !it.isExternal }
        val existing = animalId?.let { id -> local.find { it.id == id } ?: throw IOException("Animal no disponible") }
        return AnimalEditorContext(DemoInventoryData.RANCH_ID, "Rancho de ejemplo", hasEditPermission(), local, existing, isDemo = true)
    }

    private fun read(): List<AnimalRecord> {
        if (file.exists()) return gson.fromJson(file.readText(), Array<AnimalRecord>::class.java)?.toList()
            ?: throw IOException("Datos de ejemplo no disponibles")
        return DemoInventoryData.snapshot().animals.map { animal ->
            AnimalRecord(animal.id, animal.ranchId, AnimalForm(
                folio = animal.folio, siniiga = animal.siniiga.orEmpty(), sex = animal.sex,
                category = animal.category, breed = animal.breed, color = "Blanco", reason = EntryReason.PURCHASE,
                birthDate = if (animal.category.section == InventoryCategory.YOUNG) "01/01/2026" else "01/01/2020",
                reproductiveState = animal.reproductiveState.orEmpty()
            ), situation = animal.situation, lastWeightKg = animal.lastWeightKg, birthLocked = animal.id == "animal-20000")
        }
    }

    companion object { private val mutex = Mutex() }
}
