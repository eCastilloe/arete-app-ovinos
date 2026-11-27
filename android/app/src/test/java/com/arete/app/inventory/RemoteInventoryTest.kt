package com.arete.app.inventory

import com.arete.app.data.model.*
import com.arete.app.data.remote.ApiService
import com.arete.app.data.remote.dto.InventoryAnimalDto
import com.arete.app.data.remote.dto.LoginRequest
import com.arete.app.data.repository.*
import com.arete.app.ui.screens.inventory.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RemoteInventoryTest {
    private val animalJson = """{"id":"animal-1","rancho_id":"ranch-1","folio":"AIR-2253",
        "siniiga":"484000010001","sexo":"H","categoria":"vientre","situacion":"activo",
        "raza":null,"estado_reproductivo":"gestante","ultimo_peso_kg":42.5,"es_externo":false}"""
    private val userJson = """{"usuario_id":"user-1","ranchos":[
        {"rancho_id":"ranch-1","nombre":"Rancho Uno","rol":"productor","origen":"rancho",
         "puede_capturar":true,"puede_editar":true,"puede_exportar":true,"puede_corregir":false},
        {"rancho_id":"ranch-2","nombre":"Rancho Dos"}]}"""

    private fun api(responder: (Request) -> Pair<Int, String>): ApiService {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = responder(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return Retrofit.Builder().baseUrl("https://example.invalid/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }

    private fun page(items: String = animalJson, total: Int = 1, ranch: String = "ranch-1") =
        """{"rancho_id":"$ranch","rancho_nombre":"Rancho Uno","puede_editar":true,
            "total":$total,"pagina":1,"tamano":1000,"items":[$items]}"""

    @Test fun spanishContractMapsNullBreedPregnancyAndWeight() {
        val animal = Gson().fromJson(animalJson, InventoryAnimalDto::class.java).toInventoryAnimal()
        assertNull(animal.breed)
        assertEquals("Gestante", animal.reproductiveState)
        assertEquals(AnimalSex.FEMALE, animal.sex)
        assertEquals(AnimalCategory.EWE, animal.category)
        assertEquals(42.5, animal.lastWeightKg!!, 0.001)
        assertEquals("484000010001", animal.siniiga)
    }

    @Test fun categoriesSituationsAndReproductiveStatesUseApiCodes() {
        val dto = Gson().fromJson(animalJson, InventoryAnimalDto::class.java)
        listOf("vientre", "semental", "cria_lactante", "cordero_engorda", "cordera_recria")
            .zip(AnimalCategory.entries).forEach { (code, category) ->
                assertEquals(category, dto.copy(category = code).toInventoryAnimal().category)
            }
        listOf("activo", "vendido", "muerto", "desecho", "transferido")
            .zip(AnimalSituation.entries).forEach { (code, situation) ->
                assertEquals(situation, dto.copy(situation = code).toInventoryAnimal().situation)
            }
        mapOf("vacia" to "Vacía", "empadre" to "En empadre", "gestante" to "Gestante", "lactancia" to "Lactando")
            .forEach { (code, label) -> assertEquals(label, dto.copy(reproductiveState = code).toInventoryAnimal().reproductiveState) }
        assertEquals(AnimalSex.MALE, dto.copy(sex = "M").toInventoryAnimal().sex)
        assertNull(dto.copy(reproductiveState = null).toInventoryAnimal().reproductiveState)
        assertThrows(IllegalStateException::class.java) { dto.copy(category = "unknown").toInventoryAnimal() }
    }

    @Test fun firstAuthorizedRanchIsLoadedOnceWithAllSituationsAndLocalSearch() = runBlocking {
        val requests = mutableListOf<Request>()
        val repository = RemoteInventoryRepository(api { request ->
            requests.add(request)
            200 to if (request.url.encodedPath == "/auth/yo") userJson else page()
        })
        val snapshot = repository.load()
        assertFalse(snapshot.isDemo)
        assertEquals("ranch-1", snapshot.ranchId)
        assertEquals("Rancho Uno", snapshot.ranchName)
        assertEquals(2, requests.size)
        val url = requests.last().url
        assertEquals("/ranchos/ranch-1/animales", url.encodedPath)
        assertEquals("todas", url.queryParameter("situacion"))
        assertEquals("1000", url.queryParameter("tamano"))
        assertEquals("1", url.queryParameter("pagina"))
        assertEquals(1, projectInventory(snapshot, InventoryFilters(query = "air-2253")).animals.size)
        assertTrue(projectInventory(snapshot, InventoryFilters(query = "absent")).animals.isEmpty())
        assertEquals(2, requests.size)
    }

    @Test fun noAssignedRanchDoesNotFetchOrInventAnInventory() {
        var calls = 0
        val repository = RemoteInventoryRepository(api { calls++; 200 to """{"usuario_id":"u","ranchos":[]}""" })
        assertThrows(NoAssignedRanchException::class.java) { runBlocking { repository.load() } }
        assertEquals(1, calls)
    }

    @Test fun sessionRanchesSelectInventoryWithoutFetchingAnotherProfile() = runBlocking {
        val requests = mutableListOf<Request>()
        val repository = RemoteInventoryRepository(api { request ->
            requests.add(request)
            200 to page()
        }, { com.arete.app.auth.testUser().ranches })
        assertEquals("ranch-1", repository.load().ranchId)
        assertEquals(1, requests.size)
        assertEquals("/ranchos/ranch-1/animales", requests.single().url.encodedPath)
    }

    @Test fun realEmptyInventoryHasNoDemoAnimals() = runBlocking {
        val repository = RemoteInventoryRepository(api { 200 to if (it.url.encodedPath == "/auth/yo") userJson else page("", 0) })
        val snapshot = repository.load()
        assertFalse(snapshot.isDemo)
        assertTrue(snapshot.animals.isEmpty())
    }

    @Test fun partialAndWrongRanchResponsesAreNotShownAsCompleteCounts() {
        for ((json, error) in listOf(page(total = 1001) to IncompleteInventoryException::class.java,
            page(ranch = "another") to IllegalStateException::class.java,
            page(animalJson.replace("ranch-1", "another")) to IllegalStateException::class.java)) {
            val repository = RemoteInventoryRepository(api { 200 to if (it.url.encodedPath == "/auth/yo") userJson else json })
            assertThrows(error) { runBlocking { repository.load() } }
        }
    }

    @Test fun deniedOrExpiredSessionDoesNotFallBackToDemo() {
        for (code in listOf(401, 403, 500)) {
            val repository = RemoteInventoryRepository(api { code to """{"detail":"rejected"}""" })
            val error = assertThrows(HttpException::class.java) { runBlocking { repository.load() } }
            assertEquals(code, error.code())
        }
    }

    @Test fun loginUsesTheEmailFieldExpectedByRender() {
        val payload = Gson().toJsonTree(LoginRequest("example@example.invalid", "test-only")).asJsonObject
        assertTrue(payload.has("email"))
        assertFalse(payload.has("correo"))
    }
}
