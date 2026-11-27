package com.arete.app.animal

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arete.app.AreteApplication
import com.arete.app.BuildConfig
import com.arete.app.MainActivity
import com.arete.app.data.model.AnimalSex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Read-only live verification; credentials are supplied at runtime, never committed. */
class LiveAnimalDetailTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun producerReadsFemaleAndMaleAndNavigatesToEditorAndHome() = verify("producer", true)
    @Test fun workerReadsDetailWithoutEditPermission() = verify("worker", false)

    private fun verify(account: String, canEdit: Boolean) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveAnimalDetail") == "true")
        require(BuildConfig.API_BASE_URL == args.getString("apiUrl"))
        val app = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AreteApplication).container
        runBlocking {
            app.authRepository.logout()
            app.authRepository.login(requireNotNull(args.getString("${account}Email")), requireNotNull(args.getString("${account}Password"))).getOrThrow()
            require(app.authRepository.getRanches().first().let { it.ranchId == args.getString("ranchId") && it.name == "Rancho Demo" })
        }
        try {
            val inventory = runBlocking { app.inventoryRepository.load() }
            val female = inventory.animals.single { it.folio == "AIR-2253" }
            val male = inventory.animals.first { it.sex == AnimalSex.MALE && it.lastWeightKg == null }
            listOf(female, male).forEach { animal ->
                val detail = runBlocking { app.animalDetailRepository.loadDetail(animal.id) }
                assertEquals(animal.id, detail.animal.id)
                assertEquals(canEdit, detail.canEdit)
                assertFalse(detail.isDemo)
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                waitTag("inventory-search")
                compose.onNodeWithTag("inventory-list").performScrollToNode(hasTestTag("open-${female.id}"))
                compose.onNodeWithTag("open-${female.id}").performClick()
                waitTag("detail-female")
                compose.onNodeWithText("Arete AIR-2253").assertIsDisplayed()
                capture("$account-female")
                compose.onNodeWithText("40.0 kg").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("detail-circumference").assertDoesNotExist()
                capture("$account-weight")
                if (canEdit) {
                    compose.onNodeWithTag("detail-edit").performScrollTo().performClick()
                    waitTag("animal-field-FOLIO")
                    compose.onNodeWithTag("animal-field-FOLIO").assertTextContains("AIR-2253")
                    compose.onNodeWithText("Volver a la ficha").performClick()
                    waitTag("detail-female")
                } else compose.onNodeWithTag("detail-edit").assertDoesNotExist()
                compose.onNodeWithTag("detail-back").performClick()
                waitTag("inventory-list")
                // A nonempty search includes all three inventory sections.
                compose.onNodeWithTag("inventory-list").performScrollToNode(hasTestTag("inventory-search"))
                compose.onNodeWithTag("inventory-search").performTextReplacement(male.folio)
                compose.onNodeWithTag("inventory-search").performImeAction()
                compose.onNodeWithTag("inventory-list").performScrollToNode(hasTestTag("open-${male.id}"))
                compose.onNodeWithTag("open-${male.id}").performClick()
                waitTag("detail-male")
                capture("$account-male")
                compose.onNodeWithText("Sin pesaje").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("detail-circumference").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Estado reproductivo").assertDoesNotExist()
                compose.onNodeWithTag("detail-home").performScrollTo().performClick()
                compose.onNodeWithText("Leer arete").assertIsDisplayed()
                capture("$account-home")
            }
        } finally { runBlocking { app.authRepository.logout() } }
    }
    private fun waitTag(tag: String) = compose.waitUntil(130_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.waitForIdle(500, 5000)
        val dir = File(inst.targetContext.getExternalFilesDir(null), "hu20-evidence").apply { mkdirs() }
        val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
        try { File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
