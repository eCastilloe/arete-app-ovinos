package com.arete.app.data.remote

import com.arete.app.data.remote.dto.LoginRequest
import com.arete.app.data.remote.dto.LoginResponse
import com.arete.app.data.remote.dto.RefreshRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("auth/yo")
    suspend fun getCurrentUser(): Response<Map<String, String>>

    @GET("salud")
    suspend fun getHealth(): Response<Map<String, Any>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/refresh")
    fun refresh(@Body request: RefreshRequest): Call<LoginResponse>
}
