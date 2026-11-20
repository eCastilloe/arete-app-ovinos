package com.arete.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class TokenStorageTest {
    @Test fun sessionSurvivesReopeningStorageAndLogoutRemovesBothTokens() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "hu08-" + UUID.randomUUID() + ".preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var secondScope: CoroutineScope? = null
        try {
            val first = TokenStorage(PreferenceDataStoreFactory.create(scope = firstScope) { file })
            val session = AuthSession("test-access", "test-refresh", 2000, 9000)
            first.saveSession(session)
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            secondScope = reopenedScope
            val reopened = TokenStorage(PreferenceDataStoreFactory.create(scope = reopenedScope) { file })
            assertEquals(session, reopened.getSession())
            reopened.saveSession(null)
            assertNull(reopened.getSession())
        } finally {
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            secondScope?.coroutineContext?.get(Job)?.cancelAndJoin()
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
