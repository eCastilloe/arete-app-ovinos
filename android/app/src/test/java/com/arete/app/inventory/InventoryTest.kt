package com.arete.app.inventory

import com.arete.app.data.model.*
import com.arete.app.data.repository.DemoInventoryData
import com.arete.app.ui.screens.inventory.*
import org.junit.Assert.*
import org.junit.Test

class InventoryTest {
    private val fixture = DemoInventoryData.snapshot()

    @Test fun countsMatchAcceptanceExample() {
        val state = projectInventory(fixture, InventoryFilters())
        assertEquals(mapOf(InventoryCategory.FEMALES to 40, InventoryCategory.STUDS to 2, InventoryCategory.YOUNG to 25), state.counts)
        assertEquals(40, state.animals.size)
        assertEquals(3, state.dischargedCount)
    }

    @Test fun allThreeYoungCategoriesStayInYoungEvenForFemales() {
        val state = projectInventory(fixture, InventoryFilters(category = InventoryCategory.YOUNG))
        assertEquals(25, state.animals.size)
        assertEquals(setOf(AnimalCategory.SUCKLING, AnimalCategory.FATTENING, AnimalCategory.REPLACEMENT), state.animals.map { it.category }.toSet())
        assertTrue(state.animals.any { it.sex == AnimalSex.FEMALE })
    }

    @Test fun searchIgnoresCaseAndOuterSpacesAndSearchesAcrossActiveCategories() {
        val state = projectInventory(fixture, InventoryFilters(category = InventoryCategory.STUDS, query = " air-2253 "))
        assertEquals(listOf("AIR-2253"), state.animals.map { it.folio })
        assertEquals(40, state.counts[InventoryCategory.FEMALES])
    }

    @Test fun searchMatchesSiniigaOfAnotherCategory() {
        val target = fixture.animals.first { it.category == AnimalCategory.STUD }
        assertEquals(listOf(target), projectInventory(fixture, InventoryFilters(query = target.siniiga!!)).animals)
    }

    @Test fun inactiveAnimalsAreOnlyFoundInBajasIncludingTransfers() {
        val transferred = fixture.animals.first().copy(id = "transferred", folio = "BAJA-04", situation = AnimalSituation.TRANSFERRED)
        val data = fixture.copy(animals = fixture.animals + transferred)
        assertTrue(projectInventory(data, InventoryFilters(query = "BAJA")).animals.isEmpty())
        val bajas = projectInventory(data, InventoryFilters(showDischarged = true))
        assertEquals(4, bajas.animals.size)
        assertTrue(bajas.animals.none { it.situation == AnimalSituation.ACTIVE })
        assertEquals(listOf("BAJA-04"), projectInventory(data, InventoryFilters(query = "BAJA-04", showDischarged = true)).animals.map { it.folio })
    }

    @Test fun otherRanchExternalAndDeletedRecordsNeverAffectRowsCountsOrAverage() {
        val animal = fixture.animals.first()
        val extra = listOf(animal.copy(id = "other", ranchId = "otro", lastWeightKg = 999.0),
            animal.copy(id = "external", isExternal = true), animal.copy(id = "deleted", isDeleted = true))
        assertEquals(projectInventory(fixture, InventoryFilters()), projectInventory(fixture.copy(animals = fixture.animals + extra), InventoryFilters()))
    }

    @Test fun averageUsesOnlyValidLatestWeightsAndDoesNotTreatMissingAsZero() {
        val base = fixture.animals.first()
        val data = fixture.copy(animals = listOf(40.0, 60.0, null, 0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY)
            .mapIndexed { i, weight -> base.copy(id = "$i", lastWeightKg = weight) })
        val result = projectInventory(data, InventoryFilters())
        assertEquals(50.0, result.averageWeightKg!!, 0.001)
        assertEquals(2, result.weighedCount)
    }

    @Test fun emptyInventoryAndMissingWeightsHaveNoAverage() {
        assertNull(projectInventory(fixture.copy(animals = emptyList()), InventoryFilters()).averageWeightKg)
        assertNull(projectInventory(fixture.copy(animals = listOf(fixture.animals.first().copy(lastWeightKg = null))), InventoryFilters()).averageWeightKg)
    }

    @Test fun thousandAnimalFixtureHasUniqueStableIdsAndFindsLastAnimal() {
        val large = DemoInventoryData.snapshot(large = true)
        assertEquals(1000, large.animals.size)
        assertEquals(1000, large.animals.map { it.id }.toSet().size)
        val result = projectInventory(large, InventoryFilters(query = "C-380"))
        assertEquals(listOf("C-380"), result.animals.map { it.folio })
        assertEquals(1000, result.counts.values.sum())
    }
}
