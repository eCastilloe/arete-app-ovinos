package com.arete.app.data.remote

import com.arete.app.data.remote.dto.*
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*

interface AnimalApiService {
    companion object {
        // Explicit null means "clear this field" in PATCH; the default Gson converter omits it.
        fun converterFactory() = retrofit2.converter.gson.GsonConverterFactory.create(
            com.google.gson.GsonBuilder().serializeNulls().create())
    }
    @GET("razas") suspend fun breeds(): List<BreedDto>
    @GET("ranchos/{ranchId}/animales")
    suspend fun animals(@Path("ranchId") ranchId: String, @Query("pagina") page: Int,
        @Query("tamano") pageSize: Int = 1000, @Query("situacion") situation: String = "todas"): AnimalPageDto
    @GET("animales/{id}") suspend fun animal(@Path("id") id: String): AnimalDetailDto
    @POST("ranchos/{ranchId}/animales")
    suspend fun create(@Path("ranchId") ranchId: String, @Body animal: JsonObject): Response<AnimalDetailDto>
    @PATCH("animales/{id}")
    suspend fun update(@Path("id") id: String, @Body animal: JsonObject): Response<AnimalDetailDto>
}
