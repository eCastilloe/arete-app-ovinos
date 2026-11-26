package com.arete.app.auth

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arete.app.AreteApplication
import com.arete.app.BuildConfig
import com.arete.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in: exercises the real form without storing credentials or modifying ranch data. */
class LiveLoginScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun bothRolesLoginRestoreReadTagAndLogout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveAuth") == "true")
        require(BuildConfig.API_BASE_URL.startsWith("https://") && BuildConfig.API_BASE_URL == args.getString("apiUrl"))
        val container = (instrumentation.targetContext.applicationContext as AreteApplication).container
        runBlocking { container.authRepository.logout() }
        try {
            for (account in listOf("producer", "worker")) {
                val email = requireNotNull(args.getString(account + "Email"))
                val password = requireNotNull(args.getString(account + "Password"))
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    waitForText("Entrar a tu rancho")
                    compose.onNodeWithText("Usuario (correo)").performTextInput(email)
                    if (account == "producer") {
                        compose.onNodeWithText("Contraseña", substring = false).performTextInput("incorrect-test-password")
                        compose.onNodeWithText("Entrar", substring = false).performScrollTo().performClick()
                        waitForText("Usuario o contraseña incorrectos")
                        compose.onNodeWithText("Usuario o contraseña incorrectos").performScrollTo().assertIsDisplayed()
                        compose.onNodeWithText(email).assertExists()
                        capture("incorrect-password")
                    }
                    compose.onNodeWithText("Contraseña", substring = false).performScrollTo().performTextReplacement(password)
                    compose.onNodeWithText("Entrar", substring = false).performScrollTo()
                        .assertHeightIsAtLeast(56.dp).performClick()
                    waitForText("Datos consultados del rancho")
                    compose.onNodeWithText("Datos de ejemplo · prueba local").assertDoesNotExist()
                    val before = runBlocking { requireNotNull(container.tokenStorage.getSession()) }
                    assertTrue(before.ranches.isNotEmpty())
                    assertEquals(if (account == "producer") "productor" else "encargado", before.ranches.first().role)
                    capture("$account-inventory")
                    scenario.recreate()
                    waitForText("Datos consultados del rancho")
                    assertEquals(before.sessionId, runBlocking { container.tokenStorage.getSession()?.sessionId })
                }
                // Close and launch a new Activity using the persisted session.
                ActivityScenario.launch(MainActivity::class.java).use {
                    waitForText("Datos consultados del rancho")
                    compose.onNodeWithText("Inicio").performClick()
                    compose.onNodeWithText("Leer arete").assertIsDisplayed().assertHeightIsAtLeast(56.dp).performClick()
                    waitForText("Ficha del animal")
                    compose.onNodeWithText("Identificador: 00000000-0000-4000-8000-000000000001").assertIsDisplayed()
                    capture("$account-reader")
                    compose.onNodeWithText("Volver").performClick()
                    compose.onNodeWithText("Cerrar sesión").performClick()
                    waitForText("Entrar a tu rancho")
                    assertNull(runBlocking { container.tokenStorage.getSession() })
                    capture("$account-logout")
                }
            }
        } finally {
            runBlocking { container.authRepository.logout() }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(130_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "hu08-evidence").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
