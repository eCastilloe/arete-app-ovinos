package com.arete.app.data.remote

import com.arete.app.data.local.TokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenStorage: TokenStorage) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenStorage.getToken() }

        val request = chain.request().newBuilder().apply {
            if (token != null) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()

        val response = chain.proceed(request)

        if (response.code == 401 && token != null) {
            runBlocking { tokenStorage.clearToken() }
        }

        return response
    }
}