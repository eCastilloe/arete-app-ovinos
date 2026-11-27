package com.arete.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import com.arete.app.data.remote.dto.CurrentUserDto
import com.google.gson.Gson
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "session")

class TokenStorage(private val dataStore: DataStore<Preferences>) : SessionStore {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val accessExpiryKey = longPreferencesKey("access_expires_at")
    private val refreshExpiryKey = longPreferencesKey("refresh_expires_at")
    private val demoKey = booleanPreferencesKey("is_demo")
    private val userKey = stringPreferencesKey("current_user")
    private val sessionIdKey = stringPreferencesKey("session_id")
    private val gson = Gson()

    override val sessionFlow = dataStore.data.map(::readSession)

    private fun readSession(preferences: Preferences): AuthSession? {
        if (preferences[demoKey] == true) return AuthSession.demo()
        val refreshToken = preferences[refreshKey] ?: return null
        val session = AuthSession(
            accessToken = preferences[accessKey] ?: return null,
            refreshToken = refreshToken,
            accessExpiresAt = preferences[accessExpiryKey] ?: return null,
            refreshExpiresAt = preferences[refreshExpiryKey] ?: return null,
            // Compatibilidad con sesiones anteriores a la incorporación de /auth/yo.
            sessionId = preferences[sessionIdKey] ?: UUID.nameUUIDFromBytes(refreshToken.toByteArray()).toString()
        )
        val storedUser = preferences[userKey] ?: return session
        return runCatching { session.withUser(gson.fromJson(storedUser, CurrentUserDto::class.java)) }
            .getOrElse { session } // Un perfil ilegible se vuelve a consultar; no concede permisos.
    }

    private fun writeSession(preferences: MutablePreferences, session: AuthSession?) {
        preferences.clear()
        if (session != null) {
            preferences[accessKey] = session.accessToken
            preferences[refreshKey] = session.refreshToken
            preferences[accessExpiryKey] = session.accessExpiresAt
            preferences[refreshExpiryKey] = session.refreshExpiresAt
            preferences[demoKey] = session.isDemo
            preferences[sessionIdKey] = session.sessionId
            if (session.userId != null) preferences[userKey] = gson.toJson(CurrentUserDto(session.userId, session.ranches))
        }
    }

    override suspend fun saveSession(session: AuthSession?) {
        dataStore.edit { writeSession(it, session) }
    }

    // Compatibilidad con los consumidores existentes; demo nunca es un Bearer real.
    suspend fun getToken(): String? = getSession()?.takeUnless { it.isDemo }?.accessToken

    override suspend fun compareAndSet(expected: AuthSession?, replacement: AuthSession?): Boolean {
        var replaced = false
        dataStore.edit {
            if (readSession(it) == expected) {
                writeSession(it, replacement)
                replaced = true
            }
        }
        return replaced
    }
}
