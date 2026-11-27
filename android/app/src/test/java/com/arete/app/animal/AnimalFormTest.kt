package com.arete.app.animal

import com.arete.app.data.model.*
import com.arete.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.util.UUID

class AnimalFormTest {
    @get:Rule val temporary = TemporaryFolder()
    private val today = LocalDate.of(2026, 11, 19)
    private val valid = AnimalForm(folio = "NUEVA-19", breed = "Dorper", color = "Blanco", reason = EntryReason.PURCHASE, birthDate = "01/01/2022")
    private val context = AnimalEditorContext("rancho", "Rancho", true, emptyList())
    private fun errors(form: AnimalForm) = validateAnimalForm(form, context, today)
    private fun repository(file: File = File(temporary.newFolder(), "animals.json"), permission: suspend () -> Boolean = { true }) =
        DemoAnimalRepository(file, { true }, permission)

    @Test fun mandatoryFieldsAndExactBreedCatalog() {
        val errors = errors(AnimalForm())
        assertTrue(errors.keys.containsAll(listOf(AnimalField.FOLIO, AnimalField.BREED, AnimalField.COLOR, AnimalField.REASON, AnimalField.BIRTH_DATE)))
        assertEquals(9, AnimalBreeds.size)
        AnimalBreeds.forEach { assertTrue(errors(valid.copy(breed = it)).isEmpty()) }
        assertTrue(errors(valid.copy(breed = "Otra")).containsKey(AnimalField.BREED))
    }

    @Test fun siniigaOptionalAndExactBoundaries() {
        listOf("", "4841234567", "484123456789012").forEach { assertFalse(errors(valid.copy(siniiga = it)).containsKey(AnimalField.SINIIGA)) }
        listOf("484123456", "4841234567890123", "4851234567", "484123456X").forEach { assertTrue(errors(valid.copy(siniiga = it)).containsKey(AnimalField.SINIIGA)) }
    }

    @Test fun birthDatesAreStrictNotFutureAndParentsMustBeOlder() {
        listOf("31/02/2022", "29/02/2023", "2022-01-01", "20/11/2026").forEach { assertTrue(errors(valid.copy(birthDate = it)).containsKey(AnimalField.BIRTH_DATE)) }
        assertTrue(errors(valid.copy(birthDate = "29/02/2024")).isEmpty())
        val mother = AnimalRecord("mother", "rancho", valid.copy(birthDate = "01/01/2023"))
        val result = validateAnimalForm(valid.copy(mother = ParentChoice(ParentKind.LOCAL, "mother")), context.copy(records = listOf(mother)), today)
        assertTrue(result.containsKey(AnimalField.MOTHER))
    }

    @Test fun parentSelectionExcludesOtherRanchWrongSexSelfDeletedAndExternalButKeepsHistoricalParents() {
        val female = AnimalRecord("mother", "rancho", valid)
        val male = female.copy(id = "father", form = valid.copy(sex = AnimalSex.MALE, category = AnimalCategory.STUD))
        val existing = female.copy(id = "self", form = valid.copy(mother = ParentChoice(ParentKind.LOCAL, "dead")))
        val records = listOf(female, male, existing, female.copy(id = "other", ranchId = "other"),
            female.copy(id = "deleted", isDeleted = true), female.copy(id = "external", isExternal = true),
            female.copy(id = "dead", situation = AnimalSituation.DEAD))
        val data = context.copy(records = records, existing = existing)
        assertEquals(setOf("mother", "dead"), parentCandidates(data, AnimalSex.FEMALE).map { it.id }.toSet())
        assertEquals(listOf("father"), parentCandidates(data, AnimalSex.MALE).map { it.id })
        assertEquals(listOf("mother", "self"), parentCandidates(data.copy(existing = null), AnimalSex.FEMALE).map { it.id })
        assertTrue(validateAnimalForm(valid.copy(mother = ParentChoice(ParentKind.LOCAL, "father")), data, today).containsKey(AnimalField.MOTHER))
    }

    @Test fun externalParentsNeedReferenceWhileUnknownIsValid() {
        assertTrue(errors(valid.copy(mother = ParentChoice(ParentKind.EXTERNAL))).containsKey(AnimalField.MOTHER))
        assertTrue(errors(valid.copy(father = ParentChoice(ParentKind.EXTERNAL, reference = "Pajilla X / rancho origen"))).isEmpty())
        assertTrue(errors(valid).isEmpty())
    }

    @Test fun categoryAndBirthLockCannotBeBypassed() {
        assertTrue(errors(valid.copy(category = AnimalCategory.STUD)).containsKey(AnimalField.CATEGORY))
        val locked = context.copy(existing = AnimalRecord("locked", "rancho", valid, birthLocked = true))
        val changed = valid.copy(sex = AnimalSex.MALE, birthDate = "01/01/2021", birthType = "Gemelar", mother = ParentChoice(ParentKind.EXTERNAL, reference = "X"))
        val result = validateAnimalForm(changed, locked, today)
        assertTrue(result.keys.containsAll(listOf(AnimalField.SEX, AnimalField.BIRTH_DATE, AnimalField.MOTHER, AnimalField.BIRTH_TYPE)))
        assertTrue(validateAnimalForm(valid.copy(color = "Negro"), locked, today).isEmpty())
    }

    @Test fun createPersistsUuidAndEditKeepsIdentityAcrossRepositoryRestart() = runBlocking {
        val file = File(temporary.newFolder(), "animals.json")
        val repo = repository(file)
        val created = repo.save(null, valid.copy(folio = "  NUEVA-19  "), null)
        UUID.fromString(created.id)
        assertEquals(AnimalSituation.ACTIVE, created.situation)
        assertEquals("NUEVA-19", created.form.folio)
        assertEquals(71, repo.load().animals.size)
        val restarted = repository(file)
        val edited = restarted.save(created.id, created.form.copy(folio = "NUEVO-FOLIO", color = "Negro"), created.version)
        assertEquals(created.id, edited.id)
        assertEquals(2, edited.version)
        assertEquals("Negro", repository(file).loadEditor(created.id).existing!!.form.color)
        assertEquals(71, restarted.load().animals.size)
    }

    @Test fun duplicateFolioAndSiniigaReturnSpecificFieldErrorsWithoutAddingRecords() = runBlocking {
        val repo = repository()
        val duplicate = valid.copy(folio = " air-2253 ", siniiga = "484000000000")
        val result = runCatching { repo.save(null, duplicate, null) }.exceptionOrNull() as AnimalValidationException
        assertTrue(result.fields.keys.containsAll(listOf(AnimalField.FOLIO, AnimalField.SINIIGA)))
        assertEquals(70, repo.load().animals.size)
    }

    @Test fun editMayKeepOwnIdentifiersAndDoesNotRestoreDischargedAnimal() = runBlocking {
        val repo = repository()
        val old = repo.loadEditor("animal-30000").existing!!
        val edited = repo.save(old.id, old.form.copy(color = "Gris"), old.version)
        assertEquals(old.situation, edited.situation)
        assertEquals(old.id, edited.id)
    }

    @Test fun permissionIsCheckedAgainAtSaveAndRealSessionCannotUseDemoStorage() = runBlocking {
        var allowed = true
        val repo = repository(permission = { allowed })
        assertTrue(repo.loadEditor(null).canEdit)
        allowed = false
        assertTrue(runCatching { repo.save(null, valid, null) }.exceptionOrNull() is AnimalEditingDeniedException)
        assertFalse(repo.load().canEdit)
        val real = DemoAnimalRepository(File(temporary.newFolder(), "real.json"), { false })
        assertTrue(runCatching { real.loadEditor(null) }.exceptionOrNull() is InventoryUnavailableException)
        assertTrue(runCatching { real.save(null, valid, null) }.exceptionOrNull() is InventoryUnavailableException)
    }

    @Test fun staleVersionCannotOverwriteNewerChangesAndUnknownIdCannotBeEdited() = runBlocking {
        val repo = repository()
        val created = repo.save(null, valid, null)
        repo.save(created.id, created.form.copy(color = "Negro"), created.version)
        assertTrue(runCatching { repo.save(created.id, created.form.copy(color = "Gris"), created.version) }.exceptionOrNull() is AnimalEditConflictException)
        assertEquals("Negro", repo.loadEditor(created.id).existing!!.form.color)
        assertTrue(runCatching { repo.loadEditor("other-ranch-id") }.isFailure)
    }
}
