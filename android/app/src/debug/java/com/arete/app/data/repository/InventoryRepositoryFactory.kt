package com.arete.app.data.repository

import com.arete.app.data.local.TokenStorage
import com.arete.app.data.model.*
import android.content.Context
import com.arete.app.data.remote.ApiService
import java.io.File

// This entire source set is excluded from release builds. No real ranch is changed.
object InventoryRepositoryFactory {
    fun create(context: Context, tokenStorage: TokenStorage, apiService: ApiService, authRepository: AuthRepository): InventoryRepository {
        val demo = createDemoAnimals(context, tokenStorage)
        val remote = RemoteInventoryRepository(apiService, authRepository::getRanches)
        return InventoryRepository {
            if (tokenStorage.getSession()?.isDemo == true) demo.load() else remote.load()
        }
    }
}

object AnimalEditorRepositoryFactory {
    fun create(context: Context, tokenStorage: TokenStorage, remote: AnimalEditorRepository): AnimalEditorRepository {
        val demo = createDemoAnimals(context, tokenStorage)
        return object : AnimalEditorRepository {
            private suspend fun selected() = if (tokenStorage.getSession()?.isDemo == true) demo else remote
            override suspend fun loadEditor(animalId: String?) = selected().loadEditor(animalId)
            override suspend fun save(animalId: String?, form: AnimalForm, expectedVersion: Int?) = selected().save(animalId, form, expectedVersion)
        }
    }
}

private fun createDemoAnimals(context: Context, tokens: TokenStorage) = DemoAnimalRepository(
    File(context.filesDir, "hu19-demo/animals.json"), isDemoSession = { tokens.getSession()?.isDemo == true }
)

object DemoInventoryData {
    const val RANCH_ID = "rancho-de-ejemplo"

    fun snapshot(large: Boolean = false): InventorySnapshot {
        val sizes = if (large) listOf(600, 20, 380) else listOf(40, 2, 25)
        val animals = buildList {
            repeat(sizes[0]) { index ->
                add(animal(index, AnimalCategory.EWE).copy(
                    folio = if (index == 0) "AIR-2253" else "H-${index + 1}",
                    reproductiveState = listOf("Vacía", "Gestante", "Lactando", "En empadre")[index % 4],
                    lastWeightKg = if (index % 5 == 4) null else 40.0 + index % 11
                ))
            }
            repeat(sizes[1]) { index ->
                add(animal(10_000 + index, AnimalCategory.STUD).copy(
                    folio = if (index == 0) "312" else "S-${index + 1}",
                    lastWeightKg = 70.0 + index % 9
                ))
            }
            repeat(sizes[2]) { index ->
                val category = listOf(AnimalCategory.SUCKLING, AnimalCategory.FATTENING, AnimalCategory.REPLACEMENT)[index % 3]
                add(animal(20_000 + index, category).copy(
                    folio = "C-${index + 1}",
                    lastWeightKg = if (index % 4 == 3) null else 10.0 + index % 20
                ))
            }
            // In the large fixture there are exactly 1,000 total animals.
            if (!large) {
                add(animal(30_000, AnimalCategory.EWE).copy(folio = "BAJA-01", situation = AnimalSituation.SOLD))
                add(animal(30_001, AnimalCategory.SUCKLING).copy(folio = "BAJA-02", situation = AnimalSituation.DEAD))
                add(animal(30_002, AnimalCategory.STUD).copy(folio = "BAJA-03", situation = AnimalSituation.CULLED))
            }
        }
        return InventorySnapshot(RANCH_ID, "Rancho de ejemplo", animals, isDemo = true)
    }

    private fun animal(index: Int, category: AnimalCategory) = InventoryAnimal(
        id = "animal-$index",
        ranchId = RANCH_ID,
        folio = "ARETE-$index",
        siniiga = (484000000000L + index).toString(),
        sex = if (category == AnimalCategory.EWE || category == AnimalCategory.REPLACEMENT ||
            (category == AnimalCategory.SUCKLING && index % 2 == 0)) AnimalSex.FEMALE else AnimalSex.MALE,
        category = category,
        breed = "Dorper"
    )
}
