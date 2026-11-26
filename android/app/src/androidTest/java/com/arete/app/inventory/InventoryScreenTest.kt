package com.arete.app.inventory

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arete.app.data.model.InventoryCategory
import com.arete.app.data.repository.*
import com.arete.app.ui.screens.inventory.InventoryScreen
import com.arete.app.ui.screens.inventory.InventoryViewModel
import com.arete.app.ui.theme.AreteTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()

    @After fun cleanUp() { compose.runOnIdle { store.clear() } }

    private fun open(repository: InventoryRepository = InventoryRepository { DemoInventoryData.snapshot() },
                     saved: SavedStateHandle = SavedStateHandle(), fontScale: Float = 1f) {
        lateinit var model: InventoryViewModel
        compose.runOnUiThread { model = InventoryViewModel(repository, saved); store.put("inventory", model) }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                AreteTheme { InventoryScreen(onHome = {}, viewModel = model) }
            }
        }
        compose.waitUntil(10_000) { !model.state.value.isLoading }
        compose.waitForIdle()
    }

    private fun list() = compose.onNodeWithTag("inventory-list")
    private fun reveal(tag: String) { list().performScrollToNode(hasTestTag(tag)) }

    @Test fun categoriesCountsWeightsAndBajasWorkThroughTheViewModel() {
        open()
        compose.onNodeWithText("40").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("25").assertIsDisplayed()
        compose.onNodeWithTag("category-STUDS").assertHeightIsAtLeast(56.dp).performClick()
        reveal("average-weight")
        compose.onNodeWithTag("average-weight").assertTextEquals("Peso promedio: 70.5 kg")
        reveal("animal-animal-10000")
        compose.onNodeWithText("312").assertIsDisplayed()
        reveal("show-discharged")
        compose.onNodeWithTag("show-discharged").assertHeightIsAtLeast(56.dp).performClick()
        reveal("inventory-result-count")
        compose.onNodeWithTag("inventory-result-count").assertTextEquals("Bajas · 3")
        reveal("animal-animal-30000")
        compose.onNodeWithText("BAJA-01").assertIsDisplayed()
        compose.onNodeWithText("Vendido").assertIsDisplayed()
    }

    @Test fun searchFolioSiniigaNoResultsAndClear() {
        open()
        compose.onNodeWithTag("inventory-search").performTextInput("air-2253")
        reveal("inventory-result-count")
        compose.onNodeWithTag("inventory-result-count").assertTextEquals("Resultados en todos los apartados · 1")
        reveal("animal-animal-0")
        compose.onNodeWithText("AIR-2253").assertIsDisplayed()
        reveal("inventory-search")
        compose.onNodeWithTag("inventory-search").performTextReplacement("484000010000")
        reveal("animal-animal-10000")
        compose.onNodeWithText("312").assertIsDisplayed()
        reveal("inventory-search")
        compose.onNodeWithTag("inventory-search").performTextReplacement("no-existe")
        reveal("inventory-empty")
        compose.onNodeWithTag("inventory-empty").assertIsDisplayed()
        reveal("inventory-search")
        compose.onNodeWithText("Limpiar").performClick()
        compose.onNodeWithText("40").assertIsDisplayed()
    }

    @Test fun temporaryLoadFailureCanBeRetried() {
        var calls = 0
        open(InventoryRepository { if (calls++ == 0) throw java.io.IOException() else DemoInventoryData.snapshot() })
        compose.onNodeWithText("Reintentar").assertHeightIsAtLeast(56.dp).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("40").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, calls)
    }

    @Test fun searchActionAndClearReleaseInputFocus() {
        open()
        compose.onNodeWithTag("inventory-search").performClick().performTextInput("AIR-2253")
        compose.onNodeWithTag("inventory-search").performImeAction()
        compose.onNodeWithTag("inventory-search").assertIsNotFocused()
        compose.onNodeWithTag("inventory-search").performClick()
        compose.onNodeWithText("Limpiar").performClick()
        compose.onNodeWithTag("inventory-search").assertIsNotFocused()
        compose.onNodeWithText("Limpiar").assertDoesNotExist()
    }

    @Test fun unavailableRealInventoryNeverShowsExampleAnimals() {
        open(UnavailableInventoryRepository())
        compose.onNodeWithText("El inventario de tu rancho aún no está disponible. Intenta de nuevo más tarde.").assertIsDisplayed()
        compose.onNodeWithText("Rancho de ejemplo").assertDoesNotExist()
        compose.onNodeWithText("AIR-2253").assertDoesNotExist()
    }

    @Test fun noAssignedRanchShowsMessageInsteadOfInventoryControls() {
        open(InventoryRepository { throw NoAssignedRanchException() })
        compose.onNodeWithText("No tienes ranchos asignados").assertIsDisplayed()
        compose.onNodeWithTag("inventory-search").assertDoesNotExist()
        compose.onNodeWithText("Rancho de ejemplo").assertDoesNotExist()
    }

    @Test fun missingBreedAndTranslatedPregnancyAreReadable() {
        val snapshot = DemoInventoryData.snapshot()
        open(InventoryRepository { snapshot.copy(isDemo = false,
            animals = listOf(snapshot.animals.first().copy(breed = null, reproductiveState = "Gestante"))) })
        reveal("animal-animal-0")
        compose.onNodeWithText("Sin raza").assertIsDisplayed()
        compose.onNodeWithText("Gestante").assertIsDisplayed()
    }

    @Test fun savedSelectionAndLargeTextRemainUsable() {
        open(saved = SavedStateHandle(mapOf("category" to InventoryCategory.YOUNG.name)), fontScale = 1.5f)
        reveal("category-YOUNG")
        compose.onNodeWithTag("category-YOUNG").assertIsSelected()
        reveal("inventory-result-count")
        compose.onNodeWithTag("inventory-result-count").assertTextEquals("Crías · 25")
        reveal("animal-animal-20000")
        compose.onNodeWithText("C-1").assertIsDisplayed()
    }

    @Test fun thousandAnimalsCanScrollAndSearchInUnderOneSecond() {
        open(InventoryRepository { DemoInventoryData.snapshot(large = true) })
        compose.onNodeWithText("600").assertIsDisplayed()
        compose.onNodeWithText("20").assertIsDisplayed()
        compose.onNodeWithText("380").assertIsDisplayed()
        // Exercise actual lazy list scrolling across the 600 adult females.
        for (index in listOf(50, 150, 350, 550, 604, 250, 0)) {
            list().performScrollToIndex(index)
        }
        reveal("inventory-search")
        compose.onNodeWithTag("inventory-search").performClick()
        compose.waitForIdle()
        val started = SystemClock.elapsedRealtime()
        compose.onNodeWithTag("inventory-search").performTextInput("C-380")
        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Resultados en todos los apartados · 1").fetchSemanticsNodes().isNotEmpty()
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        Log.i("HU18Performance", "1000 animales: búsqueda y recomposición ${elapsed} ms")
        assertTrue("Búsqueda tardó $elapsed ms (máximo 1000)", elapsed <= 1000)
        reveal("animal-animal-20379")
        compose.onNode(hasText("C-380") and hasAnyAncestor(hasTestTag("animal-animal-20379"))).assertIsDisplayed()
    }
}
