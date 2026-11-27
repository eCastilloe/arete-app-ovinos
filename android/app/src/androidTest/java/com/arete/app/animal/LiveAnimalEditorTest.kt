package com.arete.app.animal

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arete.app.AreteApplication
import com.arete.app.BuildConfig
import com.arete.app.MainActivity
import com.arete.app.data.model.*
import com.arete.app.data.remote.dto.apiBody
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Explicit opt-in: creates one fictitious animal in the named Demo ranch. No deletion/permission changes. */
class LiveAnimalEditorTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun realOptionalValuesCanBeClearedAndWorkerCannotWrite() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveAnimalEditor") == "true")
        require(BuildConfig.API_BASE_URL == args.getString("apiUrl"))
        val app = (inst.targetContext.applicationContext as AreteApplication).container
        val folio = requireNotNull(args.getString("existingTestFolio"))
        require(folio.startsWith("HU19-"))
        runBlocking {
            app.authRepository.logout()
            app.authRepository.login(requireNotNull(args.getString("producerEmail")), requireNotNull(args.getString("producerPassword"))).getOrThrow()
            val context = app.animalEditorRepository.loadEditor(null)
            require(context.ranchId == args.getString("ranchId") && context.ranchName == "Rancho Demo")
            val old = context.records.single { it.form.folio == folio }
            require(old.form.notes.contains("HU19"))
            val changed = app.animalEditorRepository.save(old.id, old.form.copy(father = ParentChoice(),
                destination = "", conception = "", dna = "", notes = "Animal ficticio HU19: prueba de vaciado opcional"), old.version)
            val verified = app.animalEditorRepository.loadEditor(old.id).existing!!
            assertEquals(ParentKind.UNKNOWN, verified.form.father.kind)
            assertEquals("", verified.form.destination)
            assertEquals("", verified.form.conception)
            // Restore the test fixture's previous optional values, using its new version.
            app.animalEditorRepository.save(old.id, old.form, changed.version)
            app.authRepository.logout()
            app.authRepository.login(requireNotNull(args.getString("workerEmail")), requireNotNull(args.getString("workerPassword"))).getOrThrow()
            assertFalse(app.authRepository.getRanches().first().canEdit)
            assertFalse(app.animalEditorRepository.loadEditor(null).canEdit)
            // Reusing the existing folio and a stale version cannot create/overwrite a row if permissions change unexpectedly.
            val body = old.form.copy(clientId = UUID.randomUUID().toString()).apiBody(context.breedIds.getValue(old.form.breed), null)
            assertEquals(403, app.animalApiService.create(context.ranchId, body).code())
            assertEquals(403, app.animalApiService.update(old.id, old.form.apiBody(context.breedIds.getValue(old.form.breed), old.version)).code())
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Datos consultados del rancho")
            compose.onNodeWithTag("add-animal").assertDoesNotExist()
            capture("worker-readonly")
        }
        runBlocking { app.authRepository.logout() }
    }

    @Test fun realCreateDuplicateErrorsEditAndVersionConflict() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveAnimalEditor") == "true")
        require(BuildConfig.API_BASE_URL.startsWith("https://") && BuildConfig.API_BASE_URL == args.getString("apiUrl"))
        val expectedRanch = requireNotNull(args.getString("ranchId"))
        val app = (instrumentation.targetContext.applicationContext as AreteApplication).container
        runBlocking {
            app.authRepository.logout()
            app.authRepository.login(requireNotNull(args.getString("producerEmail")), requireNotNull(args.getString("producerPassword"))).getOrThrow()
            require(app.authRepository.getRanches().first().let { it.ranchId == expectedRanch && it.name == "Rancho Demo" })
        }
        val repo = app.animalEditorRepository
        val context = runBlocking { repo.loadEditor(null) }
        val mother = parentCandidates(context, AnimalSex.FEMALE).first { it.form.folio == "AIR-2253" }
        val duplicateSiniiga = context.records.first { it.form.siniiga.isNotBlank() }.form.siniiga
        val folio = "HU19-${System.currentTimeMillis()}"
        val duplicateFolio = mother.form.folio
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitTag("add-animal")
            compose.onNodeWithTag("inventory-list").performScrollToNode(hasTestTag("add-animal"))
            compose.onNodeWithTag("add-animal").performClick()
            waitTag("animal-field-FOLIO")
            input(AnimalField.FOLIO, duplicateFolio)
            input(AnimalField.SINIIGA, "123")
            choice(AnimalField.BREED, "Dorper")
            input(AnimalField.COLOR, "Blanco de prueba HU19")
            next()
            choice(AnimalField.REASON, "Registro inicial")
            input(AnimalField.BIRTH_DATE, "01/01/2026")
            compose.onNodeWithTag("animal-field-MOTHER").performScrollTo().performClick()
            compose.onNodeWithTag("parent-search").performTextInput(mother.form.folio)
            compose.onNodeWithTag("animal-option-${mother.id}").performClick()
            compose.onNodeWithTag("animal-field-FATHER").performScrollTo().performClick()
            compose.onNodeWithTag("animal-option-external").performClick()
            compose.onNodeWithTag("animal-external-FATHER").performScrollTo().performTextInput("HU19 padre externo")
            choice(AnimalField.BIRTH_TYPE, "Gemelar")
            choice(AnimalField.CONCEPTION, "Monta natural (MN)")
            next()
            choice(AnimalField.REPRODUCTIVE_STATE, "Vacía")
            choice(AnimalField.DESTINATION, "Reemplazo")
            input(AnimalField.NOTES, "Animal ficticio: prueba autorizada HU-19, no representa ganado real.")
            compose.onNodeWithTag("animal-save").performClick()
            waitTag("animal-error-SINIIGA")
            compose.onNodeWithTag("animal-error-SINIIGA").performScrollTo().assertIsDisplayed()
            capture("invalid-siniiga-local")
            input(AnimalField.SINIIGA, "")
            next(); next(); compose.onNodeWithTag("animal-save").performClick()
            waitTag("animal-error-FOLIO")
            compose.onNodeWithTag("animal-error-FOLIO").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("animal-field-COLOR").assertTextContains("Blanco de prueba HU19")
            capture("duplicate-folio-server")
            input(AnimalField.FOLIO, folio)
            input(AnimalField.SINIIGA, duplicateSiniiga)
            next(); next(); compose.onNodeWithTag("animal-save").performClick()
            waitTag("animal-error-SINIIGA")
            compose.onNodeWithTag("animal-error-SINIIGA").performScrollTo().assertIsDisplayed()
            capture("duplicate-siniiga-server")
            input(AnimalField.SINIIGA, "")
            next(); capture("parents-real")
            next(); compose.onNodeWithTag("animal-save").performClick()
            waitTag("add-animal")
            val created = runBlocking { repo.loadEditor(null).records.single { it.form.folio == folio } }
            assertEquals(expectedRanch, created.ranchId)
            assertEquals(mother.id, created.form.mother.id)
            assertEquals(ParentKind.EXTERNAL, created.form.father.kind)
            assertEquals("Reemplazo", created.form.destination)
            assertEquals(EntryReason.INITIAL_REGISTRATION, created.form.reason)
            compose.onNodeWithTag("inventory-list").performScrollToNode(hasTestTag("edit-${created.id}"))
            compose.onNodeWithTag("edit-${created.id}").performClick()
            waitTag("animal-field-FOLIO")
            input(AnimalField.COLOR, "Gris editado HU19")
            next(); next(); compose.onNodeWithTag("animal-save").performClick()
            waitTag("add-animal")
            val edited = runBlocking { repo.loadEditor(created.id).existing!! }
            assertEquals(created.id, edited.id)
            assertEquals("Gris editado HU19", edited.form.color)
            assertTrue(edited.version > created.version)
            scenario.recreate()
            waitTag("add-animal")
            compose.onNodeWithTag("inventory-list").performScrollToNode(hasTestTag("edit-${created.id}"))
            compose.onNodeWithTag("edit-${created.id}").performClick()
            waitTag("animal-field-FOLIO")
            compose.onNodeWithTag("animal-field-COLOR").performScrollTo().assertTextContains("Gris editado HU19")
            capture("edited-real")
            // Simulate another editor changing only this test animal after the screen loaded.
            runBlocking { repo.save(edited.id, edited.form.copy(notes = "Segunda edición de prueba HU19"), edited.version) }
            input(AnimalField.COLOR, "No debe sobrescribir")
            next(); next(); compose.onNodeWithTag("animal-save").performClick()
            waitText("La ficha cambió mientras la editabas. Conservamos lo escrito; copia tus cambios y vuelve a abrirla.")
            compose.onNodeWithTag("animal-general-error").performScrollTo().assertIsDisplayed()
            capture("version-conflict")
            assertEquals("Gris editado HU19", runBlocking { repo.loadEditor(edited.id).existing!!.form.color })
            // Verify deployed 409 version and 422 SINIIGA responses without a successful extra write.
            runBlocking {
                val stale = app.animalApiService.update(edited.id, edited.form.apiBody(context.breedIds.getValue("Dorper"), edited.version))
                assertEquals(409, stale.code())
                val invalid = edited.form.copy(clientId = UUID.randomUUID().toString(), folio = "$folio-X", siniiga = "123")
                    .apiBody(context.breedIds.getValue("Dorper"), null)
                assertEquals(422, app.animalApiService.create(expectedRanch, invalid).code())
            }
            compose.onNodeWithText("Volver al inventario").performClick()
            compose.onNodeWithText("Descartar").performClick()
            waitTag("add-animal")
            compose.onNodeWithText("Inicio").performClick()
            compose.onNodeWithText("Leer arete").assertIsDisplayed()
            capture("back-home")
        }
        // Read-only evidence of the real worker's permissions; do not modify its membership.
        runBlocking {
            app.authRepository.logout()
            app.authRepository.login(requireNotNull(args.getString("workerEmail")), requireNotNull(args.getString("workerPassword"))).getOrThrow()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Datos consultados del rancho")
            val canEdit = runBlocking { app.authRepository.getRanches().first().canEdit }
            if (!canEdit) compose.onNodeWithTag("add-animal").assertDoesNotExist()
            capture(if (canEdit) "worker-edit-authorized" else "worker-readonly")
        }
        runBlocking { app.authRepository.logout() }
    }
    private fun input(field: AnimalField, text: String) {
        compose.onNodeWithTag("animal-field-${field.name}").performScrollTo().performTextReplacement(text)
        compose.onNodeWithTag("animal-field-${field.name}").performImeAction()
    }
    private fun choice(field: AnimalField, label: String) {
        compose.onNodeWithTag("animal-field-${field.name}").performScrollTo().performClick()
        compose.onNodeWithTag("animal-option-$label").performScrollTo().performClick()
    }
    private fun next() = compose.onNodeWithTag("animal-next").performClick()
    private fun waitTag(tag: String) = compose.waitUntil(130_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(130_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.waitForIdle(500, 5000)
        val dir = File(inst.targetContext.getExternalFilesDir(null), "hu19-evidence").apply { mkdirs() }
        val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
        try { File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
