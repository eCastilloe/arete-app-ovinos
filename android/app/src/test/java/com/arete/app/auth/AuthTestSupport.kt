package com.arete.app.auth

import com.arete.app.data.local.AuthSession
import com.arete.app.data.local.SessionStore
import com.arete.app.data.remote.dto.LoginResponse
import com.arete.app.data.remote.dto.CurrentUserDto
import com.arete.app.data.remote.dto.RanchAccessDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Base64

const val TEST_NOW = 1_000_000L

class MemorySessions(initial: AuthSession? = null) : SessionStore {
    override val sessionFlow = MutableStateFlow(initial)
    private val mutex = Mutex()
    override suspend fun saveSession(session: AuthSession?) = mutex.withLock { sessionFlow.value = session }
    override suspend fun compareAndSet(expected: AuthSession?, replacement: AuthSession?): Boolean = mutex.withLock {
        if (sessionFlow.value != expected) false else { sessionFlow.value = replacement; true }
    }
}

fun testToken(type: String, expiry: Long, marker: String = "one"): String {
    val payload = Gson().toJson(mapOf("tipo" to type, "exp" to expiry / 1000, "sub" to "test-user", "jti" to marker))
    return "eyJhbGciOiJIUzI1NiJ9." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payload.toByteArray()) + ".test-signature"
}

fun testResponse(marker: String = "one") = LoginResponse(
    testToken("access", TEST_NOW + 60_000, marker),
    testToken("refresh", TEST_NOW + 600_000, marker), "bearer"
)

fun testSession(accessExpiry: Long = TEST_NOW + 60_000, refreshExpiry: Long = TEST_NOW + 600_000) =
    AuthSession(testToken("access", accessExpiry), testToken("refresh", refreshExpiry), accessExpiry, refreshExpiry)

fun testUser(canEdit: Boolean = true) = CurrentUserDto("test-user", listOf(
    RanchAccessDto("ranch-1", "Rancho de prueba", "encargado", "asignacion", true, canEdit, false, false),
    RanchAccessDto("ranch-2", "Segundo rancho", "productor", "propietario", true, true, true, true)
))
