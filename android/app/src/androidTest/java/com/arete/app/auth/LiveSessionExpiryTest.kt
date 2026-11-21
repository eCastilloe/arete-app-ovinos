package com.arete.app.auth

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arete.app.AreteApplication
import com.arete.app.BuildConfig
import com.arete.app.MainActivity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Opt-in, real elapsed time. Replaces only the app login session, never ranch data. */
class LiveSessionExpiryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun rejectedRefreshClearsStoredSessionAndReturnsToLogin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit live session test opt-in", args.getString("liveShortExpiry") == "true")
        val baseUrl = requireNotNull(args.getString("apiUrl"))
        require(baseUrl.startsWith("https://") && BuildConfig.API_BASE_URL == baseUrl)
        val container = (instrumentation.targetContext.applicationContext as AreteApplication).container
        runBlocking {
            container.authRepository.logout()
            assertTrue("Fresh login must succeed", container.authRepository.login(
                requireNotNull(args.getString("producerEmail")),
                requireNotNull(args.getString("producerPassword"))
            ).isSuccess)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("inventory-list").fetchSemanticsNodes().isNotEmpty()
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking {
                val session = requireNotNull(container.tokenStorage.getSession())
                // Rechazo real de esta sesión de prueba; no cambia JWT_SECRET en Render.
                container.tokenStorage.saveSession(session.copy(
                    accessToken = invalidSignature(session.accessToken),
                    refreshToken = invalidSignature(session.refreshToken)
                ))
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(95_000) {
                compose.onAllNodesWithText("Entrar a tu rancho").fetchSemanticsNodes().isNotEmpty()
            }
            assertNull(runBlocking { container.tokenStorage.getSession() })
            compose.onNodeWithText("Entrar a tu rancho").assertIsDisplayed()
            compose.onNodeWithTag("inventory-list").assertDoesNotExist()
            instrumentation.sendStatus(2, Bundle().apply {
                putString("stream", "\nHU08 rejected refresh: real server rejection cleared DataStore and displayed Login. JWT_SECRET was not changed.\n")
            })
        }
    }

    @Test fun accessExpiresAndAppRenewsTwiceWithoutReturningToLogin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit short-expiry test opt-in", args.getString("liveShortExpiry") == "true")
        val baseUrl = requireNotNull(args.getString("apiUrl"))
        require(baseUrl.startsWith("https://") && BuildConfig.API_BASE_URL == baseUrl)
        val app = instrumentation.targetContext.applicationContext as AreteApplication
        val container = app.container
        runBlocking {
            container.authRepository.logout()
            assertTrue("Fresh login must succeed", container.authRepository.login(
                requireNotNull(args.getString("producerEmail")),
                requireNotNull(args.getString("producerPassword"))
            ).isSuccess)
        }
        val rawClient = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("inventory-list").fetchSemanticsNodes().isNotEmpty()
            }
            repeat(2) { cycle ->
                scenario.moveToState(Lifecycle.State.CREATED)
                val before = runBlocking { requireNotNull(container.tokenStorage.getSession()) }
                val remaining = before.accessExpiresAt - System.currentTimeMillis()
                assertTrue("Access must use the short TTL, not the former 60 minutes", remaining in 1..65_000)
                assertTrue("Refresh must remain valid after access expires", before.refreshExpiresAt > before.accessExpiresAt)
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("stream", "\nHU08 expiry cycle ${cycle + 1}: waiting ${remaining / 1000 + 2}s; no token values logged.\n")
                })
                Thread.sleep(remaining + 2_000)
                val expiredRequest = Request.Builder().url(baseUrl + "auth/yo")
                    .header("Authorization", "Bearer " + before.accessToken).build()
                // El reloj del emulador puede diferir del servidor. Confirmar el
                // vencimiento en la API, sin adelantar relojes ni modificar tokens.
                val deadline = SystemClock.elapsedRealtime() + 65_000
                var expiredStatus: Int
                do {
                    expiredStatus = rawClient.newCall(expiredRequest).execute().use { it.code }
                    assertTrue("Expiry probe must return 200 or 401", expiredStatus == 200 || expiredStatus == 401)
                    if (expiredStatus == 200 && SystemClock.elapsedRealtime() < deadline) Thread.sleep(5_000)
                    else break
                } while (true)
                assertEquals("Server must reject the naturally expired access", 401, expiredStatus)
                scenario.moveToState(Lifecycle.State.RESUMED)
                compose.waitUntil(95_000) {
                    runBlocking { container.tokenStorage.getSession()?.accessToken?.let { it != before.accessToken } == true }
                }
                val after = runBlocking { requireNotNull(container.tokenStorage.getSession()) }
                assertTrue("Renewed access must be valid", after.accessExpiresAt > System.currentTimeMillis())
                assertTrue("Rotated refresh must be persisted", after.refreshToken != before.refreshToken)
                assertTrue("Authenticated request after renewal", runBlocking { container.apiService.getCurrentUser().isSuccessful })
                compose.onNodeWithTag("inventory-list").assertIsDisplayed()
                compose.onNodeWithText("Entrar a tu rancho").assertDoesNotExist()
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("stream", "\nHU08 expiry cycle ${cycle + 1}: expired access 401, refreshed session saved, authenticated request OK, Inventory still visible.\n")
                })
            }
        }
    }

    private fun invalidSignature(token: String): String {
        val pieces = token.split('.').toMutableList()
        val signature = pieces[2]
        pieces[2] = (if (signature[0] == 'A') "B" else "A") + signature.drop(1)
        return pieces.joinToString(".")
    }
}
