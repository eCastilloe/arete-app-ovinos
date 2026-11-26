package com.arete.app.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface SessionStore {
    val sessionFlow: Flow<AuthSession?>
    suspend fun getSession(): AuthSession? = sessionFlow.first()
    suspend fun saveSession(session: AuthSession?)
    // Evita que una respuesta tardía restaure una sesión que el usuario ya cerró.
    suspend fun compareAndSet(expected: AuthSession?, replacement: AuthSession?): Boolean
}
