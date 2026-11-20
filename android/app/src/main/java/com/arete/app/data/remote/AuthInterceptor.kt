package com.arete.app.data.remote

import com.arete.app.data.local.AuthSession
import com.arete.app.data.local.SessionStore
import com.arete.app.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val authApi: ApiService,
    private val now: () -> Long = System::currentTimeMillis
) : Interceptor {
    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = synchronized(refreshLock) { activeSession() }
        val request = chain.request().newBuilder().apply {
            removeHeader("Authorization")
            if (session != null && !session.isDemo) header("Authorization", "Bearer " + session.accessToken)
        }.build()
        val response = chain.proceed(request)
        // Contrato del equipo: ante 401, renovar y repetir una sola vez.
        if (response.code == 401 && session != null && !session.isDemo) {
            val replacement = try {
                synchronized(refreshLock) {
                    // Una respuesta tardía no debe actuar sobre otra sesión.
                    if (runBlocking { sessionStore.getSession() } != session) null else renew(session)
                }
            } catch (error: IOException) {
                response.close()
                throw error
            }
            if (replacement == null) return response
            // No repetir cuerpos que solo se pueden enviar una vez.
            if (request.body?.isOneShot() == true || request.body?.isDuplex() == true) return response
            response.close()
            val retried = chain.proceed(request.newBuilder()
                .header("Authorization", "Bearer " + replacement.accessToken).build())
            if (retried.code == 401) runBlocking { sessionStore.compareAndSet(replacement, null) }
            return retried
        }
        return response
    }

    private fun activeSession(): AuthSession? {
        val session = runBlocking { sessionStore.getSession() } ?: return null
        if (session.isDemo) return null
        if (!session.isValid(now())) {
            runBlocking { sessionStore.compareAndSet(session, null) }
            throw IOException("La sesión expiró.")
        }
        if (session.accessExpiresAt > now()) return session

        return renew(session) ?: throw IOException("La sesión expiró.")
    }

    private fun renew(session: AuthSession): AuthSession? {
        if (!session.isValid(now())) {
            runBlocking { sessionStore.compareAndSet(session, null) }
            return null
        }
        // Este cliente no lleva el interceptor: evita una renovación recursiva.
        val result = authApi.refresh(RefreshRequest(session.refreshToken)).execute()
        if (!result.isSuccessful) {
            result.errorBody()?.close()
            if (result.code() == 401) {
                runBlocking { sessionStore.compareAndSet(session, null) }
                return null
            }
            throw IOException("No se pudo renovar la sesión.")
        }
        val replacement = try {
            AuthSession.fromResponse(requireNotNull(result.body()), now())
        } catch (error: RuntimeException) {
            throw IOException("Respuesta de sesión inválida.", error)
        }
        if (!runBlocking { sessionStore.compareAndSet(session, replacement) }) {
            throw IOException("La sesión cambió durante la renovación.")
        }
        return replacement
    }
}
