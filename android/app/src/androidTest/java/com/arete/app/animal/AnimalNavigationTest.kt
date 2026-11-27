package com.arete.app.animal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arete.app.AreteApplication
import com.arete.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class AnimalNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun actualDemoFactoriesOpenInventoryEditorAndHome() {
        val app = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AreteApplication).container
        runBlocking { app.authRepository.saveDemoSession() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("add-animal").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("inventory-list").performScrollToNode(hasTestTag("add-animal"))
                compose.onNodeWithTag("add-animal").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("animal-field-FOLIO").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Datos de ejemplo · guardado solo en este dispositivo").assertExists()
                compose.onNodeWithText("Volver al inventario").performClick()
                compose.onNodeWithText("Inicio").performClick()
                compose.onNodeWithText("Leer arete").assertIsDisplayed()
            }
        } finally { runBlocking { app.authRepository.logout() } }
    }
}
