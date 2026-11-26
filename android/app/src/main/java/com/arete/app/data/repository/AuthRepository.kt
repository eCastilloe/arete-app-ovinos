package com.arete.app.data.repository

import com.arete.app.BuildConfig
import com.arete.app.data.local.AuthSession
import com.arete.app.data.local.SessionStore
import com.arete.app.data.remote.ApiService
import com.arete.app.data.remote.dto.LoginRequest
import com.arete.app.data.remote.dto.RanchAccessDto
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

class AuthRepository(
    private val apiService: ApiService,
    private val sessionStore: SessionStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val authenticatedApi: ApiService = apiService
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val isLoggedIn: Flow<Boolean> = sessionStore.sessionFlow.flatMapLatest { session ->
        flow {
            val valid = session != null && session.isValid(now()) && (!session.isDemo || BuildConfig.DEBUG)
            emit(valid)
            if (valid && !session.isDemo) {
                delay((session.refreshExpiresAt - now()).coerceAtLeast(0))
                emit(false)
            }
        }
    }.distinctUntilChanged()

    suspend fun login(email: String, password: String): Result<Unit> = try {
        val previous = sessionStore.getSession()
        val response = apiService.login(LoginRequest(email.trim(), password))
        val tokens = AuthSession.fromResponse(response, now())
        // Cliente público: usa el token recién recibido, sin publicar una sesión incompleta.
        val userResponse = apiService.getCurrentUser("Bearer " + tokens.accessToken)
        if (!userResponse.isSuccessful) throw HttpException(userResponse)
        val session = tokens.withUser(requireNotNull(userResponse.body()))
        check(sessionStore.compareAndSet(previous, session))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveDemoSession() {
        check(BuildConfig.DEBUG)
        sessionStore.saveSession(AuthSession.demo())
    }

    suspend fun logout() = sessionStore.saveSession(null)

    suspend fun getRanches(): List<RanchAccessDto> {
        if (sessionStore.getSession()?.userId == null) validateSession().getOrThrow()
        val session = requireNotNull(sessionStore.getSession())
        check(!session.isDemo && session.isValid(now()) && session.userId != null)
        return session.ranches
    }

    // Al volver a la app se comprueba la sesión; sin red se conserva hasta que expire.
    suspend fun validateSession(): Result<Unit> = try {
        val session = sessionStore.getSession()
        if (session != null && !session.isDemo) {
            val response = authenticatedApi.getCurrentUser()
            if (!response.isSuccessful) throw HttpException(response)
            val user = requireNotNull(response.body())
            while (true) {
                val current = sessionStore.getSession()
                // Un cierre u otro login no puede recibir los permisos de una petición anterior.
                check(current != null && current.sessionId == session.sessionId)
                val updated = current.withUser(user)
                if (sessionStore.compareAndSet(current, updated)) break
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { Result.failure(e) }
}
