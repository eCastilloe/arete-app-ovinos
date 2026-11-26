package com.arete.app.inventory

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arete.app.AreteApplication
import com.arete.app.BuildConfig
import com.arete.app.MainActivity
import com.arete.app.data.model.InventoryCategory
import com.arete.app.ui.screens.inventory.InventoryFilters
import com.arete.app.ui.screens.inventory.projectInventory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in, read-only ranch queries. Credentials are local runner arguments, never source code. */
class LiveInventoryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val container get() = (instrumentation.targetContext.applicationContext as AreteApplication).container

    @Before fun requireOptIn() {
        assumeTrue("Requires liveInventory=true", args.getString("liveInventory") == "true")
        require(BuildConfig.API_BASE_URL.startsWith("https://") && BuildConfig.API_BASE_URL == args.getString("apiUrl"))
    }

    @Test fun producerReadsRealInventory() = exerciseRealInventory("producer")
    @Test fun workerReadsRealInventory() = exerciseRealInventory("worker")

    private fun exerciseRealInventory(account: String) {
        runBlocking {
            container.authRepository.logout()
            assertTrue("Test account must log in", container.authRepository.login(
                requireNotNull(args.getString(account + "Email")),
                requireNotNull(args.getString(account + "Password"))
            ).isSuccess)
        }
        try {
            val snapshot = runBlocking { container.inventoryRepository.load() }
            assertFalse(snapshot.isDemo)
            val expected = projectInventory(snapshot, InventoryFilters())
            val expectedCounts = args.getString("expectedCounts")?.split(',')?.map { it.toInt() }
            if (expectedCounts != null) {
                assertEquals(expectedCounts, InventoryCategory.entries.map { expected.counts[it] })
            }
            args.getString("expectedFemaleAverageKg")?.toDouble()?.let {
                assertEquals(it, requireNotNull(expected.averageWeightKg), 0.001)
            }
            args.getString("expectedWeighedFemales")?.toInt()?.let {
                assertEquals(it, expected.weighedCount)
            }
            var searchMillis: Long? = null
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithText("Inicio").performClick()
                compose.onNodeWithText("Leer arete").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
                compose.onNodeWithText("Ver inventario").assertHeightIsAtLeast(56.dp).performClick()
                compose.waitUntil(65_000) {
                    compose.onAllNodesWithTag("inventory-search").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(snapshot.ranchName).assertIsDisplayed()
                compose.onNodeWithText("Datos de ejemplo · prueba local").assertDoesNotExist()
                InventoryCategory.entries.forEach { category ->
                    compose.onNodeWithTag("category-${category.name}")
                        .assert(hasText(expected.counts.getValue(category).toString()))
                        .assertHeightIsAtLeast(56.dp)
                }
                capture("$account-inventory")
                val list = compose.onNodeWithTag("inventory-list")
                fun reveal(tag: String) { list.performScrollToNode(hasTestTag(tag)) }
                reveal("average-weight")
                val average = expected.averageWeightKg?.let { String.format(java.util.Locale.US, "%.1f", it) }
                compose.onNodeWithTag("average-weight").assertTextEquals(
                    if (average == null) "Peso promedio: sin pesajes" else "Peso promedio: $average kg")
                if (expected.weighedCount > 0) {
                    reveal("average-weight")
                    compose.onNodeWithText("Último pesaje de ${expected.weighedCount} de ${expected.animals.size} animales")
                        .assertExists()
                }
                capture("$account-average")

                // Visit each real category and verify its list/average, including unweighed animals.
                listOf(InventoryCategory.STUDS, InventoryCategory.YOUNG).forEach { category ->
                    reveal("category-${category.name}")
                    compose.onNodeWithTag("category-${category.name}").performClick()
                    val categoryState = projectInventory(snapshot, InventoryFilters(category = category))
                    reveal("inventory-result-count")
                    compose.onNodeWithTag("inventory-result-count")
                        .assertTextEquals("${category.label} · ${categoryState.animals.size}")
                    reveal("average-weight")
                    val categoryAverage = categoryState.averageWeightKg?.let { String.format(java.util.Locale.US, "%.1f", it) }
                    compose.onNodeWithTag("average-weight").assertTextEquals(
                        if (categoryAverage == null) "Peso promedio: sin pesajes" else "Peso promedio: $categoryAverage kg")
                    capture("$account-${category.name.lowercase()}")
                }

                reveal("inventory-search")
                val requiredFolio = args.getString("expectedFolio")
                val target = if (requiredFolio != null) {
                    requireNotNull(expected.animals.firstOrNull { it.folio == requiredFolio }) { "Missing required live folio" }
                } else expected.animals.firstOrNull { it.folio == "AIR-2253" } ?: expected.animals.firstOrNull()
                if (target != null) {
                    val start = SystemClock.elapsedRealtime()
                    compose.onNodeWithTag("inventory-search").performTextReplacement(target.folio.lowercase())
                    compose.waitForIdle()
                    compose.onNodeWithTag("animal-${target.id}").assertExists()
                    searchMillis = SystemClock.elapsedRealtime() - start
                    assertTrue("Live search/recomposition took $searchMillis ms", searchMillis <= 1_000)
                    compose.onNodeWithTag("inventory-search").performImeAction()
                    compose.onNodeWithTag("inventory-search").assertIsNotFocused()
                    reveal("animal-${target.id}")
                    compose.onNodeWithText(target.folio).assertIsDisplayed()
                    if (target.breed == null) compose.onNodeWithText("Sin raza").assertIsDisplayed()
                    target.reproductiveState?.let { compose.onNodeWithText(it).assertIsDisplayed() }
                    target.lastWeightKg?.let {
                        compose.onNodeWithText(String.format(java.util.Locale.US, "%.1f kg", it)).assertIsDisplayed()
                    }
                    capture("$account-search")
                }
                args.getString("expectedSiniiga")?.let { number ->
                    val chipTarget = requireNotNull(snapshot.animals.firstOrNull { it.siniiga == number })
                    reveal("inventory-search")
                    compose.onNodeWithTag("inventory-search").performTextReplacement(number)
                    compose.onNodeWithTag("inventory-search").performImeAction()
                    compose.onNodeWithTag("inventory-search").assertIsNotFocused()
                    reveal("animal-${chipTarget.id}")
                    compose.onNodeWithText(chipTarget.folio).assertIsDisplayed()
                    compose.onNodeWithText("SINIIGA $number").assertIsDisplayed()
                    capture("$account-siniiga")
                }
                reveal("inventory-search")
                compose.onNodeWithTag("inventory-search").performTextReplacement("HU18-SIN-COINCIDENCIAS")
                compose.onNodeWithTag("inventory-search").performImeAction()
                reveal("inventory-empty")
                compose.onNodeWithTag("inventory-empty").assertIsDisplayed()
                capture("$account-no-results")
                reveal("inventory-search")
                compose.onNodeWithText("Limpiar").performClick()
                reveal("show-discharged")
                compose.onNodeWithTag("show-discharged").performClick()
                reveal("inventory-result-count")
                compose.onNodeWithTag("inventory-result-count").assertTextEquals("Bajas · ${expected.dischargedCount}")
                capture("$account-bajas")
                compose.onNodeWithText("Inicio").performClick()
                compose.onNodeWithText("Leer arete").assertIsDisplayed()
                compose.onNodeWithText("Ver inventario").assertIsDisplayed()
                capture("$account-home")
            }
            instrumentation.sendStatus(2, Bundle().apply {
                putString("stream", "\nHU18 $account: real inventory OK; active counts=${InventoryCategory.entries.map { expected.counts[it] }}; bajas=${expected.dischargedCount}; weighed=${expected.weighedCount}; averageKg=${expected.averageWeightKg}; searchMillis=$searchMillis; searchTarget=${targetAvailable(expected.animals.size)}.\n")
            })
        } finally {
            runBlocking { container.authRepository.logout() }
        }
    }

    @Test fun demoKeepsReaderAndInventoryNavigation() {
        runBlocking { container.authRepository.saveDemoSession() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithText("Inicio").performClick()
                compose.onNodeWithText("Leer arete").assertHeightIsAtLeast(56.dp).performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Ficha del animal").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Identificador: 00000000-0000-4000-8000-000000000001").assertIsDisplayed()
                capture("demo-reader")
                compose.onNodeWithText("Volver").performClick()
                compose.onNodeWithText("Ver inventario").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("inventory-search").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Datos de ejemplo · prueba local").assertIsDisplayed()
                compose.onNodeWithTag("category-FEMALES").assert(hasText("40"))
                compose.onNodeWithTag("category-STUDS").assert(hasText("2"))
                compose.onNodeWithTag("category-YOUNG").assert(hasText("25"))
                capture("demo-inventory")
                compose.onNodeWithText("Inicio").performClick()
                compose.onNodeWithText("Leer arete").assertIsDisplayed()
            }
        } finally {
            runBlocking { container.authRepository.logout() }
        }
    }

    private fun targetAvailable(size: Int) = if (size > 0) "present" else "empty ranch (no matching-row check)"

    private fun capture(name: String) {
        compose.waitForIdle()
        // IME transitions run outside Compose; avoid capturing a keyboard mid-animation.
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "hu18-evidence").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
