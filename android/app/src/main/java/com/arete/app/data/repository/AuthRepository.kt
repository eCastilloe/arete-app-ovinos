package com.arete.app.data.repository

import com.arete.app.BuildConfig
import com.arete.app.data.local.AuthSession
import com.arete.app.data.local.SessionStore
import com.arete.app.data.remote.ApiService
import com.arete.app.data.remote.dto.LoginRequest
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
        val session = AuthSession.fromResponse(response, now())
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

    // Al volver a la app se comprueba la sesión; sin red se conserva hasta que expire.
    suspend fun validateSession(): Result<Unit> = try {
        val session = sessionStore.getSession()
        if (session != null && !session.isDemo) {
            val response = authenticatedApi.getCurrentUser()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw java.io.IOException("No se pudo comprobar la sesión.")
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { Result.failure(e) }
}
