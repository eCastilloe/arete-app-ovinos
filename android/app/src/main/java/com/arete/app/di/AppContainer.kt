package com.arete.app.di

import android.content.Context
import com.arete.app.BuildConfig
import com.arete.app.data.local.TokenStorage
import com.arete.app.data.remote.AnimalApiService
import com.arete.app.data.repository.RemoteAnimalEditorRepository
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
import com.arete.app.data.repository.AnimalDetailRepositoryFactory
import com.arete.app.data.repository.RemoteAnimalDetailRepository
import com.arete.app.data.local.AnimalPhotoStore
import com.arete.app.alerts.AlertDataSourceFactory
import com.arete.app.alerts.AlertRunner
import com.arete.app.alerts.NotificationAlertPresenter
import com.arete.app.alerts.PreferencesShownAlertStore
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
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val publicClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun <T> createService(client: OkHttpClient, type: Class<T>,
        converter: retrofit2.Converter.Factory = GsonConverterFactory.create()): T = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(converter)
        .build()
        .create(type)

    private val authApi = createService(publicClient, ApiService::class.java)
    private val authenticatedClient = publicClient.newBuilder()
        .addInterceptor(AuthInterceptor(tokenStorage, authApi))
        .build()
    val apiService = createService(authenticatedClient, ApiService::class.java)
    val authRepository = AuthRepository(authApi, tokenStorage, authenticatedApi = apiService)
    val syncRepository = SyncRepository(database.pendingOperationDao())
    val inventoryRepository = InventoryRepositoryFactory.create(context, tokenStorage, apiService, authRepository)
    val animalPhotoStore = AnimalPhotoStore(context.applicationContext)
    val animalApiService = createService(authenticatedClient, AnimalApiService::class.java, AnimalApiService.converterFactory())
    private val remoteAnimalEditor = RemoteAnimalEditorRepository(animalApiService, {
        authRepository.validateSession().getOrThrow()
        authRepository.getRanches()
    }, animalPhotoStore::find, animalPhotoStore::associate)
    val animalEditorRepository = AnimalEditorRepositoryFactory.create(context, tokenStorage, remoteAnimalEditor)
    val animalDetailRepository = AnimalDetailRepositoryFactory.create(context, tokenStorage,
        RemoteAnimalDetailRepository(animalApiService, {
            authRepository.validateSession().getOrThrow()
            authRepository.getRanches()
        }, animalPhotoStore::find))
    // HU-54 cambia solo esta línea por el lector NFC/QR real.
    val lectorArete: LectorArete = SimulatedLectorArete()

    // HU-39: avisos locales de parto y destete.
    val alertPresenter = NotificationAlertPresenter(context)
    val alertRunner = AlertRunner(
        AlertDataSourceFactory.create(tokenStorage),
        PreferencesShownAlertStore(context),
        alertPresenter
    )
}
