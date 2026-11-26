package com.arete.app.auth

import com.arete.app.data.local.AuthSession
import com.arete.app.data.remote.ApiService
import com.arete.app.data.remote.AuthInterceptor
import com.arete.app.data.repository.AuthRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var publicApi: ApiService
    private val sessions = MemorySessions()
    private val clock = { TEST_NOW }
    private fun json(body: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(Gson().toJson(body))
    private fun api(client: OkHttpClient) = Retrofit.Builder().baseUrl(server.url("/")).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    private fun privateApi() = api(OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(sessions, publicApi, clock)).build())

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        publicApi = api(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build())
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun loginUsesEmailContractAndRestoresSession() = runBlocking {
        server.enqueue(json(testResponse()))
        server.enqueue(json(testUser()))
        val repository = AuthRepository(publicApi, sessions, clock)
        assertTrue(repository.login("  prueba@example.test  ", "test password").isSuccess)
        val request = server.takeRequest()
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("/auth/login", request.path)
        assertEquals("prueba@example.test", body["email"].asString)
        assertFalse(body.has("correo"))
        assertNull(request.getHeader("Authorization"))
        assertEquals(testResponse().refreshToken, sessions.getSession()?.refreshToken)
        assertTrue(AuthRepository(publicApi, sessions, clock).isLoggedIn.first())
        val profileRequest = server.takeRequest()
        assertEquals("/auth/yo", profileRequest.path)
        assertEquals("Bearer " + testResponse().accessToken, profileRequest.getHeader("Authorization"))
        assertEquals(testUser().ranches, sessions.getSession()?.ranches)
        assertEquals("test-user", sessions.getSession()?.userId)
    }

    @Test fun failedOrMalformedProfileDoesNotPublishPartialLogin() = runBlocking {
        for (profile in listOf(MockResponse().setResponseCode(503), json(mapOf("usuario_id" to "test-user")),
            json(testUser().copy(userId = "another-user")))) {
            server.enqueue(json(testResponse()))
            server.enqueue(profile)
            assertTrue(AuthRepository(publicApi, sessions, clock).login("a@b.test", "test").isFailure)
            assertNull(sessions.getSession())
        }
    }

    @Test fun userWithoutRanchesCanLoginWithoutInventedPermissions() = runBlocking {
        server.enqueue(json(testResponse()))
        server.enqueue(json(testUser().copy(ranches = emptyList())))
        val repository = AuthRepository(publicApi, sessions, clock)
        assertTrue(repository.login("a@b.test", "test").isSuccess)
        assertTrue(repository.getRanches().isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test fun restorationReplacesCachedPermissionsAndInventoryReadsThemWithoutAnotherRequest() = runBlocking {
        sessions.saveSession(testSession().withUser(testUser()))
        server.enqueue(json(testUser(canEdit = false)))
        val repository = AuthRepository(publicApi, sessions, clock, privateApi())
        assertTrue(repository.validateSession().isSuccess)
        assertFalse(repository.getRanches().first().canEdit)
        assertEquals(testUser(false).ranches, sessions.getSession()?.ranches)
        assertEquals(1, server.requestCount)
    }

    @Test fun restorationCanRenewThenSaveProfileOnTheRenewedSession() = runBlocking {
        val previous = testSession(accessExpiry = TEST_NOW - 1000).withUser(testUser())
        sessions.saveSession(previous)
        server.enqueue(json(testResponse("restored")))
        server.enqueue(json(testUser(false)))
        assertTrue(AuthRepository(publicApi, sessions, clock, privateApi()).validateSession().isSuccess)
        assertEquals(testResponse("restored").accessToken, sessions.getSession()?.accessToken)
        assertEquals(previous.sessionId, sessions.getSession()?.sessionId)
        assertEquals(testUser(false).ranches, sessions.getSession()?.ranches)
    }

    @Test fun temporaryRestorationFailureKeepsCachedSessionAndPermissions() = runBlocking {
        val original = testSession().withUser(testUser())
        sessions.saveSession(original)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(AuthRepository(publicApi, sessions, clock, privateApi()).validateSession().isFailure)
        assertEquals(original, sessions.getSession())
    }

    @Test fun lateProfileCannotRestoreLogoutOrOverwriteAnotherLogin() = runBlocking {
        val repository = AuthRepository(publicApi, sessions, clock, privateApi())
        for (replacement in listOf(null, testSession().withUser(testUser(false)))) {
            sessions.saveSession(testSession().withUser(testUser()))
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    runBlocking { sessions.saveSession(replacement) }
                    return json(testUser())
                }
            }
            assertTrue(repository.validateSession().isFailure)
            assertEquals(replacement, sessions.getSession())
        }
    }

    @Test fun refreshPreservesRanchesAndLogoutClearsTheWholeSession() = runBlocking {
        val previous = testSession(accessExpiry = TEST_NOW - 1000).withUser(testUser())
        sessions.saveSession(previous)
        server.enqueue(json(testResponse("renewed")))
        server.enqueue(json(mapOf("ok" to true)))
        assertTrue(privateApi().getHealth().isSuccessful)
        assertEquals(previous.ranches, sessions.getSession()?.ranches)
        assertEquals(previous.sessionId, sessions.getSession()?.sessionId)
        AuthRepository(publicApi, sessions, clock).logout()
        assertNull(sessions.getSession())
    }

    @Test fun incorrectPasswordDoesNotCreateSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(AuthRepository(publicApi, sessions, clock).login("a@b.test", "bad").isFailure)
        assertNull(sessions.getSession())
    }

    @Test fun incompleteSuccessResponseDoesNotCreateSession() = runBlocking {
        server.enqueue(json(mapOf("access_token" to "invalid", "token_type" to "bearer")))
        assertTrue(AuthRepository(publicApi, sessions, clock).login("a@b.test", "test").isFailure)
        assertNull(sessions.getSession())
    }

    @Test fun expiredAccessIsRenewedBeforeProtectedRequest() = runBlocking {
        sessions.saveSession(testSession(accessExpiry = TEST_NOW - 1000))
        server.enqueue(json(testResponse("renewed")))
        server.enqueue(json(mapOf("ok" to true)))
        assertTrue(privateApi().getHealth().isSuccessful)
        val refresh = server.takeRequest()
        assertEquals("/auth/refresh", refresh.path)
        assertNull(refresh.getHeader("Authorization"))
        assertTrue(JsonParser.parseString(refresh.body.readUtf8()).asJsonObject.has("refresh_token"))
        assertEquals("Bearer " + testResponse("renewed").accessToken, server.takeRequest().getHeader("Authorization"))
        assertEquals(testResponse("renewed").refreshToken, sessions.getSession()?.refreshToken)
    }

    @Test fun rejectedRefreshClearsSessionWithoutProtectedRequest() = runBlocking {
        sessions.saveSession(testSession(accessExpiry = TEST_NOW - 1000))
        server.enqueue(MockResponse().setResponseCode(401))
        try { privateApi().getHealth(); fail("Expected failure") } catch (_: IOException) { }
        assertNull(sessions.getSession())
        assertEquals(1, server.requestCount)
    }

    @Test fun temporaryRefreshFailureKeepsSession() = runBlocking {
        val previous = testSession(accessExpiry = TEST_NOW - 1000)
        sessions.saveSession(previous)
        server.enqueue(MockResponse().setResponseCode(503))
        try { privateApi().getHealth(); fail("Expected failure") } catch (_: IOException) { }
        assertEquals(previous, sessions.getSession())
    }

    @Test fun rejected401RefreshSignsOutWithoutRetryingProtectedRequest() = runBlocking {
        sessions.saveSession(testSession())
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(401, privateApi().getHealth().code())
        assertNull(sessions.getSession())
        assertEquals(2, server.requestCount)
        assertEquals("/salud", server.takeRequest().path)
        assertEquals("/auth/refresh", server.takeRequest().path)
    }

    @Test fun server401RenewsAndRetriesOnce() = runBlocking {
        sessions.saveSession(testSession())
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json(testResponse("after-401")))
        server.enqueue(json(mapOf("usuario_id" to "test-user")))
        assertTrue(privateApi().getCurrentUser().isSuccessful)
        assertEquals("/auth/yo", server.takeRequest().path)
        val refresh = server.takeRequest()
        assertEquals("/auth/refresh", refresh.path)
        assertNull(refresh.getHeader("Authorization"))
        assertEquals("Bearer " + testResponse("after-401").accessToken, server.takeRequest().getHeader("Authorization"))
        assertEquals(3, server.requestCount)
    }

    @Test fun second401SignsOutWithoutLoop() = runBlocking {
        sessions.saveSession(testSession())
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json(testResponse("still-rejected")))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(401, privateApi().getCurrentUser().code())
        assertNull(sessions.getSession())
        assertEquals(3, server.requestCount)
    }

    @Test fun temporaryFailureAfter401KeepsSession() = runBlocking {
        val previous = testSession()
        sessions.saveSession(previous)
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(503))
        try { privateApi().getCurrentUser(); fail("Expected failure") } catch (_: IOException) { }
        assertEquals(previous, sessions.getSession())
        assertEquals(2, server.requestCount)
    }

    @Test fun late401CannotClearNewerSession() = runBlocking {
        sessions.saveSession(testSession())
        val newer = AuthSession.fromResponse(testResponse("new-login"), TEST_NOW)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                runBlocking { sessions.saveSession(newer) }
                return MockResponse().setResponseCode(401)
            }
        }
        privateApi().getHealth()
        assertEquals(newer, sessions.getSession())
    }

    @Test fun logoutDuringRefreshCannotRestoreSession() = runBlocking {
        sessions.saveSession(testSession(accessExpiry = TEST_NOW - 1000))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                runBlocking { sessions.saveSession(null) }
                return json(testResponse("late"))
            }
        }
        try { privateApi().getHealth(); fail("Expected failure") } catch (_: IOException) { }
        assertNull(sessions.getSession())
        assertEquals(1, server.requestCount)
    }

    @Test fun expiredRefreshCannotRestoreOrAccessNetwork() = runBlocking {
        sessions.saveSession(testSession(TEST_NOW - 2000, TEST_NOW - 1000))
        assertFalse(AuthRepository(publicApi, sessions, clock).isLoggedIn.first())
        try { privateApi().getHealth(); fail("Expected failure") } catch (_: IOException) { }
        assertEquals(0, server.requestCount)
        assertNull(sessions.getSession())
    }

    @Test fun demoDoesNotSendInventedCredentials() = runBlocking {
        sessions.saveSession(AuthSession.demo())
        server.enqueue(json(mapOf("ok" to true)))
        privateApi().getHealth()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
}
