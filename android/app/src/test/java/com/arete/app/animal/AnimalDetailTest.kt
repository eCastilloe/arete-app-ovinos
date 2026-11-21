package com.arete.app.animal

import com.arete.app.data.model.*
import com.arete.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnimalDetailTest {
    @Test fun exactAcceptanceExample() {
        assertEquals("3 meses 11 días", animalAge(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 12))?.label)
    }
    @Test fun accumulatesYearsAsMonths() {
        assertEquals("14 meses 2 días", animalAge(LocalDate.of(2025, 8, 1), LocalDate.of(2026, 10, 3))?.label)
    }
    @Test fun calendarHandlesLeapYearsAndMonthEnds() {
        assertEquals(AnimalAge(0, 28), animalAge(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)))
        assertEquals(AnimalAge(12, 1), animalAge(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 3, 1)))
    }
    @Test fun noNegativeOrInventedAges() {
        assertNull(animalAge(null, LocalDate.of(2026, 11, 12)))
        assertNull(animalAge(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 12)))
        assertEquals("0 meses 0 días", animalAge(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12))?.label)
        assertNull(parseBirthDate("31/02/2026"))
    }
    @Test fun singularMonthAndDay() {
        assertEquals("1 mes 1 día", animalAge(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 11, 12))?.label)
    }

    private val record = AnimalRecord("animal", "r1", AnimalForm(folio = "79",
        mother = ParentChoice(ParentKind.LOCAL, "mother"), father = ParentChoice(ParentKind.EXTERNAL, reference = "S-100")))
    private val mother = AnimalRecord("mother", "r1", AnimalForm(folio = "45"))
    private fun repository(animal: AnimalRecord? = record, records: List<AnimalRecord> = listOf(record, mother)) =
        LocalAnimalDetailRepository(object : AnimalEditorRepository {
            override suspend fun loadEditor(animalId: String?) = AnimalEditorContext("r1", "Rancho 1", false, records, animal, isDemo = true)
            override suspend fun save(animalId: String?, form: AnimalForm, expectedVersion: Int?): AnimalRecord = error("Reading must never save")
        })

    @Test fun readerCanConsultWithoutEditPermission() = runBlocking {
        val detail = repository().loadDetail("animal")
        assertFalse(detail.canEdit)
        assertEquals("45", detail.motherLabel)
        assertEquals("Externo · S-100", detail.fatherLabel)
        assertEquals(record, detail.animal)
    }
    @Test fun rejectsMissingWrongRanchExternalDeletedAndMismatchedId() = runBlocking {
        listOf(null, record.copy(ranchId = "r2"), record.copy(isExternal = true), record.copy(isDeleted = true), record.copy(id = "another"))
            .forEach { invalid ->
                try { repository(invalid).loadDetail("animal"); fail("Should reject unauthorized animal") }
                catch (_: AnimalDetailNotFoundException) { }
            }
    }
    @Test fun doesNotExposeParentsFromAnotherRanch() = runBlocking {
        val detail = repository(records = listOf(record, mother.copy(ranchId = "r2"))).loadDetail("animal")
        assertEquals("No disponible", detail.motherLabel)
    }
    @Test fun dischargedAnimalsRemainReadable() = runBlocking {
        val detail = repository(record.copy(situation = AnimalSituation.DEAD)).loadDetail("animal")
        assertEquals(AnimalSituation.DEAD, detail.animal.situation)
    }
}
