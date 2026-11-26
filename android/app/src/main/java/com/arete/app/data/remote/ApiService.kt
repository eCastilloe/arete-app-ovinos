package com.arete.app.data.remote

import com.arete.app.data.remote.dto.LoginRequest
import com.arete.app.data.remote.dto.LoginResponse
import com.arete.app.data.remote.dto.CurrentUserDto
import com.arete.app.data.remote.dto.InventoryPageDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("salud")
    suspend fun getHealth(): Response<Map<String, Any>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("auth/yo")
    suspend fun getCurrentUser(): Response<CurrentUserDto>

    @GET("ranchos/{ranchId}/animales")
    suspend fun getInventory(
        @Path("ranchId") ranchId: String,
        @Query("situacion") situation: String = "todas",
        @Query("tamano") pageSize: Int = 1000,
        @Query("pagina") page: Int = 1
    ): InventoryPageDto
}
