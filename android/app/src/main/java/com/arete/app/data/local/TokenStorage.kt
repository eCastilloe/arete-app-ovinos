package com.arete.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

class TokenStorage(private val context: Context) {

    private val tokenKey = stringPreferencesKey("access_token")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[tokenKey]
    }

    suspend fun getToken(): String? = tokenFlow.first()

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences -> preferences[tokenKey] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { preferences -> preferences.remove(tokenKey) }
    }
}