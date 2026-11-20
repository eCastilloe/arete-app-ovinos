package com.arete.app.auth

import androidx.test.platform.app.InstrumentationRegistry
import com.arete.app.data.local.AuthSession
import com.arete.app.data.local.SessionStore
import com.arete.app.data.remote.ApiService
import com.arete.app.data.remote.AuthInterceptor
import com.arete.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Opt-in: runner arguments supply test credentials; none are stored in the repository. */
class LiveAuthTest {
    private class Sessions : SessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(null)
        override suspend fun saveSession(session: AuthSession?) { sessionFlow.value = session }
        override suspend fun compareAndSet(expected: AuthSession?, replacement: AuthSession?): Boolean =
            sessionFlow.compareAndSet(expected, replacement)
    }

    @Test fun liveLoginRefreshAndDefinitive401() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Live API tests require explicit runner arguments", args.getString("liveAuth") == "true")
        val baseUrl = requireNotNull(args.getString("apiUrl"))
        require(baseUrl.startsWith("https://"))
        val sessions = Sessions()
        fun api(client: OkHttpClient) = Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        val publicClient = OkHttpClient.Builder().connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS).build()
        val publicApi = api(publicClient)
        val privateApi = api(publicClient.newBuilder().addInterceptor(AuthInterceptor(sessions, publicApi)).build())
        val repository = AuthRepository(publicApi, sessions, authenticatedApi = privateApi)

        for (key in listOf("producer", "worker")) {
            assertTrue("Login $key", repository.login(requireNotNull(args.getString(key + "Email")),
                requireNotNull(args.getString(key + "Password"))).isSuccess)
            assertTrue("Bearer $key", privateApi.getCurrentUser().isSuccessful)
            val original = requireNotNull(sessions.getSession())
            // Alter only the access signature: real server 401 must trigger a real refresh.
            sessions.saveSession(original.copy(accessToken = invalidSignature(original.accessToken)))
            assertTrue("401 refresh and retry $key", privateApi.getCurrentUser().isSuccessful)
            val renewed = requireNotNull(sessions.getSession())
            assertNotEquals(original.accessToken, renewed.accessToken)
            // Both signatures invalid: real 401 from refresh must clear only this test session.
            sessions.saveSession(renewed.copy(accessToken = invalidSignature(renewed.accessToken),
                refreshToken = invalidSignature(renewed.refreshToken)))
            assertEquals(401, privateApi.getCurrentUser().code())
            assertNull(sessions.getSession())
        }
    }

    private fun invalidSignature(token: String): String {
        val pieces = token.split('.').toMutableList()
        val signature = pieces[2]
        pieces[2] = (if (signature[0] == 'A') "B" else "A") + signature.drop(1)
        return pieces.joinToString(".")
    }
}
