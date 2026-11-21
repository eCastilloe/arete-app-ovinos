package com.arete.app.di

import android.content.Context
import com.arete.app.BuildConfig
import com.arete.app.data.local.TokenStorage
import com.arete.app.data.remote.ApiService
import com.arete.app.data.remote.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import androidx.room.Room
import com.arete.app.data.local.AppDatabase
import com.arete.app.data.repository.AuthRepository
import com.arete.app.data.repository.SyncRepository
import com.arete.app.data.repository.InventoryRepositoryFactory

class AppContainer(context: Context) {

    val tokenStorage = TokenStorage(context)

    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "arete.db"
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val publicClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun createApi(client: OkHttpClient): ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    private val authApi = createApi(publicClient)
    private val authenticatedClient = publicClient.newBuilder()
        .addInterceptor(AuthInterceptor(tokenStorage, authApi))
        .build()
    val apiService = createApi(authenticatedClient)
    val authRepository = AuthRepository(authApi, tokenStorage, authenticatedApi = apiService)
    val syncRepository = SyncRepository(database.pendingOperationDao())
    val inventoryRepository = InventoryRepositoryFactory.create(tokenStorage)
}
