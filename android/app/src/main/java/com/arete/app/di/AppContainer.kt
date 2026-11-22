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
import com.arete.app.data.repository.AnimalEditorRepositoryFactory
import com.arete.app.data.local.AnimalPhotoStore
import com.arete.app.reader.LectorArete
import com.arete.app.reader.SimulatedLectorArete

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
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStorage))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    val authRepository = AuthRepository(apiService, tokenStorage)
    val syncRepository = SyncRepository(database.pendingOperationDao())
    val inventoryRepository = InventoryRepositoryFactory.create(context, tokenStorage)
    val animalEditorRepository = AnimalEditorRepositoryFactory.create(context, tokenStorage)
    val animalPhotoStore = AnimalPhotoStore(context.applicationContext)
    // HU-54 cambia solo esta línea por el lector NFC/QR real.
    val lectorArete: LectorArete = SimulatedLectorArete()
}
