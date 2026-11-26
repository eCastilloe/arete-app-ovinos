package com.arete.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.Base64
import com.arete.app.data.remote.dto.CurrentUserDto
import com.arete.app.data.remote.dto.RanchAccessDto
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

class TokenStorageTest {
    @Test fun sessionSurvivesReopeningStorageAndLogoutRemovesBothTokens() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "hu08-" + UUID.randomUUID() + ".preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var secondScope: CoroutineScope? = null
        try {
            val first = TokenStorage(PreferenceDataStoreFactory.create(scope = firstScope) { file })
            val access = "header." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"test-user\"}".toByteArray()) + ".signature"
            val session = AuthSession(access, "test-refresh", 2000, 9000).withUser(
                CurrentUserDto("test-user", listOf(
                    RanchAccessDto("ranch-1", "Rancho prueba", "encargado", "asignacion", true, false, false, false),
                    RanchAccessDto("ranch-2", "Segundo rancho", "productor", "propietario", true, true, true, true)
                ))
            )
            first.saveSession(session)
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            secondScope = reopenedScope
            val reopened = TokenStorage(PreferenceDataStoreFactory.create(scope = reopenedScope) { file })
            assertEquals(session, reopened.getSession())
            assertEquals(session.accessToken, reopened.getToken())
            reopened.saveSession(null)
            assertNull(reopened.getSession())
            assertNull(reopened.getToken())
        } finally {
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            secondScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun oldSessionWithoutProfileRemainsReadableForServerRestoration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "hu08-" + UUID.randomUUID() + ".preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            store.edit {
                it[stringPreferencesKey("access_token")] = "legacy-access"
                it[stringPreferencesKey("refresh_token")] = "legacy-refresh"
                it[longPreferencesKey("access_expires_at")] = 2000
                it[longPreferencesKey("refresh_expires_at")] = 9000
            }
            val storage = TokenStorage(store)
            val session = requireNotNull(storage.getSession())
            assertEquals(session, storage.getSession())
            assertNull(session.userId)
            assertTrue(session.ranches.isEmpty())
            assertTrue(storage.compareAndSet(session, session.copy(accessToken = "renewed")))
            storage.saveSession(AuthSession.demo())
            assertNull(storage.getToken())
            assertEquals(AuthSession.demo(), storage.getSession())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun lateRefreshCannotOverwriteLogout() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "hu08-" + UUID.randomUUID() + ".preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val storage = TokenStorage(PreferenceDataStoreFactory.create(scope = scope) { file })
            val old = AuthSession("old-access", "old-refresh", 2000, 9000)
            storage.saveSession(old)
            storage.saveSession(null)
            assertFalse(storage.compareAndSet(old, old.copy(accessToken = "new-access")))
            assertNull(storage.getSession())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
