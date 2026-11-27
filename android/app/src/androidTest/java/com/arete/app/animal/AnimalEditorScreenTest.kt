package com.arete.app.animal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arete.app.data.local.AnimalPhotoStore
import com.arete.app.data.model.*
import com.arete.app.data.repository.*
import com.arete.app.ui.screens.animal.*
import com.arete.app.ui.screens.inventory.*
import com.arete.app.ui.theme.AreteTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AnimalEditorScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val android = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(android.cacheDir, "hu19-test-${UUID.randomUUID()}.json")
    private val repo = DemoAnimalRepository(file, { true })
    private lateinit var model: AnimalEditorViewModel
    private var backCalls = 0
    private val valid = AnimalForm(folio = "TEST-HU19", breed = "Dorper", color = "Blanco", birthDate = "01/01/2022", reason = EntryReason.PURCHASE)

    @After fun cleanUp() { compose.runOnIdle { store.clear() }; file.delete() }

    private fun open(repository: AnimalEditorRepository = repo, saved: SavedStateHandle = SavedStateHandle(), fontScale: Float = 1f) {
        compose.runOnUiThread { model = AnimalEditorViewModel(repository, savedState = saved); store.put("editor", model) }
        compose.setContent {
            val state by model.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                AreteTheme { AnimalEditorContent(state, model::change, model::sex, model::step, model::save, model::load, { backCalls++ }) }
            }
        }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }
    private fun input(field: AnimalField, text: String) {
        compose.onNodeWithTag("animal-field-${field.name}").performScrollTo().performTextReplacement(text)
    }
    private fun next() { compose.onNodeWithTag("animal-next").performClick() }
    private fun fillValid() { compose.runOnIdle { model.change(AnimalField.FOLIO) { valid } } }
    private fun save() { compose.runOnIdle { model.step(2) }; compose.onNodeWithTag("animal-save").performClick(); compose.waitUntil(10_000) { !model.state.value.saving } }

    @Test fun requiredErrorsKeepTypedTextAndKeyboardCanReleaseFocus() {
        open()
        input(AnimalField.FOLIO, "MI-FOLIO")
        compose.onNodeWithTag("animal-field-FOLIO").performImeAction()
        compose.onNodeWithTag("animal-field-FOLIO").assertIsNotFocused()
        next(); next()
        compose.onNodeWithTag("animal-save").assertHeightIsAtLeast(56.dp).performClick()
        compose.onNodeWithTag("animal-error-BREED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("animal-field-FOLIO").performScrollTo().assertTextContains("MI-FOLIO")
        assertTrue(model.state.value.errors.containsKey(AnimalField.REASON))
        assertNull(model.state.value.savedId)
    }

    @Test fun selectorsShowCorrectParentsExternalAndUnknownAndBreedCatalog() {
        open()
        compose.onNodeWithTag("animal-field-BREED").performScrollTo().performClick()
        compose.onNodeWithText("Dorper").performClick()
        assertEquals("Dorper", model.state.value.form.breed)
        next()
        compose.onNodeWithTag("animal-field-MOTHER").performScrollTo().performClick()
        compose.onNodeWithText("Externa").assertIsDisplayed()
        compose.onNodeWithTag("animal-option-unknown").assertTextEquals("Desconocida").assertIsDisplayed()
        compose.onNodeWithTag("parent-search").performTextInput("312")
        compose.onNodeWithText("312 · ♂ Macho · Activo").assertDoesNotExist()
        compose.onNodeWithText("No encontramos ese folio.").assertIsDisplayed()
        compose.onNodeWithText("Externa").performClick()
        compose.onNodeWithTag("animal-external-MOTHER").performScrollTo().performTextInput("Rancho vecino / 45")
        compose.onNodeWithTag("animal-field-FATHER").performScrollTo().performClick()
        compose.onNodeWithText("Externo").assertIsDisplayed()
        compose.onNodeWithTag("animal-option-unknown").assertTextEquals("Desconocido").assertIsDisplayed()
        compose.onNodeWithTag("parent-search").performTextInput("312")
        compose.onNodeWithText("312 · ♂ Macho · Activo").performClick()
        assertEquals(ParentKind.EXTERNAL, model.state.value.form.mother.kind)
        assertEquals("animal-10000", model.state.value.form.father.id)
    }

    @Test fun repositoryValidationAppearsAtFieldAndCorrectionCanBeSaved() {
        open(); fillValid()
        input(AnimalField.FOLIO, "air-2253")
        input(AnimalField.SINIIGA, "484000000000")
        save()
        compose.onNodeWithTag("animal-error-FOLIO").performScrollTo().assertTextEquals("Ese folio ya existe en este rancho. Escribe otro.")
        compose.onNodeWithTag("animal-error-SINIIGA").performScrollTo().assertTextEquals("Ese SINIIGA ya está registrado.")
        assertEquals("Blanco", model.state.value.form.color)
        input(AnimalField.FOLIO, "TEST-HU19")
        input(AnimalField.SINIIGA, "")
        save()
        compose.waitUntil(10_000) { model.state.value.savedId != null }
        val result = runBlocking { repo.loadEditor(model.state.value.savedId).existing!! }
        assertEquals("TEST-HU19", result.form.folio)
        assertEquals(AnimalSituation.ACTIVE, result.situation)
        assertEquals(71, runBlocking { repo.load() }.animals.size)
    }

    @Test fun temporarySaveFailurePreservesDraftAndRetryWorks() {
        var calls = 0
        val flaky = object : AnimalEditorRepository by repo {
            override suspend fun save(animalId: String?, form: AnimalForm, expectedVersion: Int?): AnimalRecord {
                if (calls++ == 0) throw IOException("simulated")
                return repo.save(animalId, form, expectedVersion)
            }
        }
        open(flaky); fillValid(); save()
        compose.onNodeWithTag("animal-general-error").performScrollTo().assertTextEquals("No pudimos guardar. Conservamos tus datos; intenta de nuevo.")
        assertEquals(valid, model.state.value.form)
        save()
        compose.waitUntil(10_000) { model.state.value.savedId != null }
        assertEquals(2, calls)
    }

    @Test fun permissionDeniedHidesFormAndInventoryActions() {
        val readOnly = DemoAnimalRepository(file, { true }, { false })
        open(readOnly)
        compose.onNodeWithTag("animal-permission-denied").assertIsDisplayed()
        compose.onNodeWithTag("animal-field-FOLIO").assertDoesNotExist()
        compose.onNodeWithTag("animal-save").assertDoesNotExist()
        compose.runOnIdle { model.save() }
        assertFalse(file.exists())
    }

    @Test fun readOnlyInventoryHidesAddAndEditEvenWithNavigationCallbacks() {
        val state = projectInventory(DemoInventoryData.snapshot().copy(canEdit = false), InventoryFilters())
        compose.setContent { AreteTheme { InventoryContent(state, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("add-animal").assertDoesNotExist()
        compose.onNodeWithTag("inventory-list").performScrollToNode(hasTestTag("animal-animal-0"))
        compose.onNodeWithTag("edit-animal-0").assertDoesNotExist()
    }

    @Test fun birthLinkedFieldsAreDisabledAndDiscardNeedsConfirmation() {
        open(saved = SavedStateHandle(mapOf("animalId" to "animal-20000")))
        compose.onNodeWithTag("animal-sex-FEMALE").performScrollTo().assertIsNotEnabled()
        next()
        compose.onNodeWithTag("animal-field-BIRTH_DATE").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("animal-field-MOTHER").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { model.change(AnimalField.COLOR) { it.copy(color = "Gris") } }
        compose.onNodeWithText("Volver al inventario").performClick()
        compose.onNodeWithText("¿Salir sin guardar?").assertIsDisplayed()
        compose.onNodeWithText("Seguir editando").performClick()
        assertEquals(0, backCalls)
        compose.onNodeWithText("Volver al inventario").performClick()
        compose.onNodeWithText("Descartar").performClick()
        assertEquals(1, backCalls)
    }

    @Test fun photoImportCreatesPrivateReadableImageAndRejectsInvalidData() {
        val source = File(android.cacheDir, "hu19-source-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val photos = AnimalPhotoStore(android)
        val result = File(photos.import(Uri.fromFile(source)))
        val decoded = BitmapFactory.decodeFile(result.path)
        assertTrue(decoded.width <= 1600 && decoded.height <= 1600)
        decoded.recycle()
        assertTrue(result.canonicalPath.startsWith(File(android.filesDir, "animal-photos").canonicalPath))
        source.writeText("not an image")
        assertTrue(runCatching { photos.import(Uri.fromFile(source)) }.isFailure)
        source.delete(); result.delete()
    }

    @Test fun draftAndStepSurviveViewModelRecreation() {
        val saved = SavedStateHandle()
        open(saved = saved)
        val draft = valid.copy(registration = "UNO-19", dna = "ADN-19", breeder = "Criador de ejemplo", owner = "Propietario de ejemplo",
            mother = ParentChoice(ParentKind.EXTERNAL, reference = "Referencia 19"), conception = "Inseminación artificial (IA)", notes = "Borrador local")
        lateinit var restored: AnimalEditorViewModel
        compose.runOnIdle {
            model.change(AnimalField.FOLIO) { draft }; model.step(1)
            val copied = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) })
            restored = AnimalEditorViewModel(repo, savedState = copied)
            store.put("restored", restored)
        }
        compose.waitUntil(10_000) { !restored.state.value.loading }
        assertEquals(draft, restored.state.value.form)
        assertEquals(1, restored.state.value.step)
        assertTrue(restored.state.value.dirty)
    }

    @Test fun largeTextCanScrollThroughAllStepsAndSave() {
        open(fontScale = 1.5f); fillValid()
        compose.onNodeWithTag("animal-field-COLOR").performScrollTo().assertIsDisplayed()
        next()
        compose.onNodeWithTag("animal-field-FATHER").performScrollTo().assertIsDisplayed()
        next()
        compose.onNodeWithTag("animal-field-NOTES").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("animal-save").assertIsDisplayed().assertHeightIsAtLeast(56.dp).performClick()
        compose.waitUntil(10_000) { model.state.value.savedId != null }
    }

    @Test fun revokedPermissionIsShownAndPreventsSaveAfterFormWasOpened() {
        var allowed = true
        open(DemoAnimalRepository(file, { true }, { allowed })); fillValid()
        allowed = false
        save()
        compose.onNodeWithTag("animal-permission-denied").assertIsDisplayed()
        compose.onNodeWithTag("animal-save").assertDoesNotExist()
        assertFalse(file.exists())
    }
}
