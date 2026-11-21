package com.arete.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

class TokenStorage(private val dataStore: DataStore<Preferences>) : SessionStore {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val accessExpiryKey = longPreferencesKey("access_expires_at")
    private val refreshExpiryKey = longPreferencesKey("refresh_expires_at")
    private val demoKey = booleanPreferencesKey("is_demo")

    override val sessionFlow = dataStore.data.map(::readSession)

    private fun readSession(preferences: Preferences): AuthSession? {
        if (preferences[demoKey] == true) return AuthSession.demo()
        return AuthSession(
            accessToken = preferences[accessKey] ?: return null,
            refreshToken = preferences[refreshKey] ?: return null,
            accessExpiresAt = preferences[accessExpiryKey] ?: return null,
            refreshExpiresAt = preferences[refreshExpiryKey] ?: return null
        )
    }

    private fun writeSession(preferences: MutablePreferences, session: AuthSession?) {
        preferences.clear()
        if (session != null) {
            preferences[accessKey] = session.accessToken
            preferences[refreshKey] = session.refreshToken
            preferences[accessExpiryKey] = session.accessExpiresAt
            preferences[refreshExpiryKey] = session.refreshExpiresAt
            preferences[demoKey] = session.isDemo
        }
    }

    override suspend fun saveSession(session: AuthSession?) {
        dataStore.edit { writeSession(it, session) }
    }

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
