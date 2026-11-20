package com.arete.app.auth

import com.arete.app.data.remote.ApiService
import com.arete.app.data.remote.dto.*
import com.arete.app.data.repository.AuthRepository
import com.arete.app.ui.screens.login.LoginViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessions = MemorySessions()
    private var calls = 0
    private var handler: suspend (LoginRequest) -> LoginResponse = { testResponse() }
    private val api = object : ApiService {
        override suspend fun login(request: LoginRequest): LoginResponse { calls++; return handler(request) }
        override suspend fun getHealth(): Response<Map<String, Any>> = error("Not used")
        override fun refresh(request: RefreshRequest): Call<LoginResponse> = error("Not used")
    }
    private fun model() = LoginViewModel(AuthRepository(api, sessions) { TEST_NOW })
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun emptyFormDoesNotSendRequest() = runTest(dispatcher) {
        val model = model()
        model.login()
        runCurrent()
        assertEquals(0, calls)
        assertEquals("Escribe tu correo y contraseña.", model.state.value.error)
    }

    @Test fun errorPreservesEmailAndShowsGenericCredentialsMessage() = runTest(dispatcher) {
        handler = { throw HttpException(Response.error<LoginResponse>(401, "{}".toResponseBody())) }
        val model = model()
        model.changeEmail("prueba@example.test")
        model.changePassword("incorrecta")
        model.login()
        runCurrent()
        assertEquals("prueba@example.test", model.state.value.email)
        assertEquals("Usuario o contraseña incorrectos", model.state.value.error)
        assertFalse(model.state.value.isLoading)
    }

    @Test fun waitingRequestBlocksDuplicateSubmissionsAndSuccessClearsPassword() = runTest(dispatcher) {
        val response = CompletableDeferred<LoginResponse>()
        handler = { response.await() }
        val model = model()
        model.changeEmail("prueba@example.test")
        model.changePassword("test password")
        model.login()
        model.login()
        runCurrent()
        assertTrue(model.state.value.isLoading)
        assertEquals(1, calls)
        response.complete(testResponse())
        runCurrent()
        assertEquals("", model.state.value.password)
        assertNull(model.state.value.error)
        assertNotNull(sessions.getSession())
    }

    @Test fun networkFailureAllowsRetryWithoutLosingUsername() = runTest(dispatcher) {
        handler = { throw java.io.IOException("offline") }
        val model = model()
        model.changeEmail("prueba@example.test")
        model.changePassword("test password")
        model.login()
        runCurrent()
        assertEquals("prueba@example.test", model.state.value.email)
        assertTrue(model.state.value.error!!.contains("internet"))
        handler = { testResponse() }
        model.login()
        runCurrent()
        assertNull(model.state.value.error)
        assertEquals(2, calls)
    }

    @Test fun sessionExpiresWhileAppRemainsOpen() = runTest(dispatcher) {
        sessions.saveSession(testSession(refreshExpiry = TEST_NOW + 2000))
        val states = mutableListOf<Boolean>()
        val repository = AuthRepository(api, sessions) { TEST_NOW + testScheduler.currentTime }
        val collector = launch { repository.isLoggedIn.take(2).toList(states) }
        runCurrent()
        assertEquals(listOf(true), states)
        advanceTimeBy(2001)
        runCurrent()
        assertEquals(listOf(true, false), states)
        collector.join()
    }
}
