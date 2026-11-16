package com.arete.app.data.repository

import com.arete.app.data.local.TokenStorage
import com.arete.app.data.remote.ApiService
import com.arete.app.data.remote.dto.LoginRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val apiService: ApiService,
    private val tokenStorage: TokenStorage
) {

    val isLoggedIn: Flow<Boolean> = tokenStorage.tokenFlow.map { token -> token != null }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val response = apiService.login(LoginRequest(email = email, password = password))
            tokenStorage.saveToken(response.accessToken)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveDemoSession() {
        tokenStorage.saveToken(DEMO_TOKEN)
    }

    suspend fun logout() {
        tokenStorage.clearToken()
    }

    companion object {
        private const val DEMO_TOKEN = "demo-token"
    }
}