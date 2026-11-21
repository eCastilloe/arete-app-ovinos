package com.arete.app.animal

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arete.app.data.model.*
import com.arete.app.data.repository.*
import com.arete.app.ui.screens.animal.*
import com.arete.app.ui.screens.inventory.*
import com.arete.app.ui.theme.AreteTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AnimalDetailScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val day = LocalDate.of(2026, 11, 12)
    private val female = AnimalRecord("a1", "r1", AnimalForm(folio = "79", birthDate = "01/08/2026",
        breed = "Dorper", color = "Blanco", reproductiveState = "Gestante"), lastWeightKg = 40.5,
        lastTesticularCircumferenceCm = 32.0)
    private fun detail(animal: AnimalRecord = female, edit: Boolean = true) =
        AnimalDetail(animal, "Rancho de ejemplo", "Desconocida", "Desconocido", edit, true)
    private var backCalls = 0
    private var homeCalls = 0
    private var edited: String? = null

    @After fun cleanup() { compose.runOnIdle { store.clear() } }
    private fun open(value: AnimalDetail, scale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                AreteTheme { AnimalDetailContent(AnimalDetailState(false, value, onDate = day), {}, { backCalls++ }, { homeCalls++ }, { edited = it }) }
            }
        }
    }
    @Test fun femaleHasAgeLabelPinkHeaderAndNeverCircumference() {
        open(detail())
        compose.onNodeWithTag("detail-female").assertIsDisplayed()
        compose.onNodeWithText("♀ Hembra").assertIsDisplayed()
        compose.onNodeWithTag("detail-age").performScrollTo().assertTextEquals("3 meses 11 días")
        compose.onNodeWithText("Gestante").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("40.5 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail-circumference").assertDoesNotExist()
    }
    @Test fun maleHasBlueHeaderAndCircumference() {
        open(detail(female.copy(form = female.form.copy(sex = AnimalSex.MALE, category = AnimalCategory.STUD, destination = "Semental"))))
        compose.onNodeWithTag("detail-male").assertIsDisplayed()
        compose.onNodeWithText("♂ Macho").assertIsDisplayed()
        compose.onNodeWithText("32.0 cm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Estado reproductivo").assertDoesNotExist()
    }
    @Test fun missingWeightUnknownDateAndDischargeAreExplicit() {
        open(detail(female.copy(form = female.form.copy(birthDate = ""), lastWeightKg = null, situation = AnimalSituation.SOLD)))
        compose.onNodeWithTag("detail-age").performScrollTo().assertTextEquals("Edad no disponible")
        compose.onNodeWithText("Vendido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin pesaje").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin foto").performScrollTo().assertIsDisplayed()
    }
    @Test fun readOnlyHasNoEditingAndCanGoBack() {
        open(detail(edit = false))
        compose.onNodeWithTag("detail-edit").assertDoesNotExist()
        compose.onNodeWithTag("detail-back").assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(1, backCalls)
    }
    @Test fun largeTextStillAllowsEditingAndHome() {
        open(detail(), 1.5f)
        compose.onNodeWithTag("detail-edit").performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        assertEquals("a1", edited)
        compose.onNodeWithTag("detail-home").performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(1, homeCalls)
    }
    @Test fun failedLoadRetriesAndRefreshClearsStaleData() {
        var attempts = 0
        lateinit var model: AnimalDetailViewModel
        compose.runOnUiThread {
            model = AnimalDetailViewModel(AnimalDetailRepository {
                attempts++
                if (attempts != 2) throw IOException()
                detail()
            }, SavedStateHandle(mapOf("animalId" to "a1")), { day })
            store.put("detail", model)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            AreteTheme { AnimalDetailContent(state, model::reload, {}, {}, {}) }
        }
        compose.waitUntil(10_000) { !model.state.value.loading }
        compose.onNodeWithTag("detail-error").assertIsDisplayed()
        compose.onNodeWithTag("detail-retry").performClick()
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.detail != null }
        compose.onNodeWithText("Arete 79").assertIsDisplayed()
        compose.runOnIdle { model.reload() }
        compose.waitUntil(10_000) { !model.state.value.loading }
        compose.onNodeWithText("Arete 79").assertDoesNotExist()
        assertNull(model.state.value.detail)
    }
    @Test fun inventoryAllowsReadingWithoutEditingAndPassesImmutableId() {
        var selected: String? = null
        compose.setContent {
            AreteTheme { InventoryContent(InventoryUiState(isLoading = false, ranchName = "Rancho de ejemplo",
                animals = listOf(female.toInventory()), canEdit = false), {}, {}, {}, {}, {},
                onOpen = { selected = it }) }
        }
        compose.onNodeWithTag("open-a1").performScrollTo().performClick()
        assertEquals("a1", selected)
        compose.onNodeWithTag("edit-a1").assertDoesNotExist()
    }
}
