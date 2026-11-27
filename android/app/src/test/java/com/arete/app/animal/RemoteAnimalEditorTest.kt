package com.arete.app.animal

import com.arete.app.data.model.*
import com.arete.app.data.remote.AnimalApiService
import com.arete.app.data.remote.dto.*
import com.arete.app.data.repository.*
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RemoteAnimalEditorTest {
    @Test fun retrofitPatchActuallyTransmitsNullsOnTheWire() = runBlocking {
        var sent = ""
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            sent = buffer.readUtf8()
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(409).message("test").body("{}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = retrofit2.Retrofit.Builder().baseUrl("https://example.invalid/").client(client)
            .addConverterFactory(AnimalApiService.converterFactory()).build().create(AnimalApiService::class.java)
        api.update(record.id, valid.apiBody(42, 2))
        val body = com.google.gson.JsonParser.parseString(sent).asJsonObject
        assertTrue(body.has("siniiga") && body["siniiga"].isJsonNull)
        assertTrue(body.has("madre_id") && body["madre_id"].isJsonNull)
        assertTrue(body.has("observaciones") && body["observaciones"].isJsonNull)
        assertFalse(body.has("motivo_ingreso"))
    }
    private val ranch = RanchAccessDto("ranch", "Rancho", "productor", "rancho", true, true, true, false)
    private val valid = AnimalForm(clientId = "00000000-0000-4000-8000-000000000019", folio = "HU19", breed = "Dorper", color = "Blanco",
        reason = EntryReason.INITIAL_REGISTRATION, birthDate = "02/03/2022")
    private val record = AnimalDetailDto(valid.clientId!!, "ranch", "HU19", "H", "vientre", 2,
        breed = "Dorper", color = "Blanco", birthDate = "2022-03-02", reason = "registro_inicial")

    private inner class Api : AnimalApiService {
        var writes = 0
        var reads = 0
        var body: JsonObject? = null
        var response: Response<AnimalDetailDto> = Response.success(record)
        var current = record
        var allowed = true
        var pageItems = listOf(record)
        override suspend fun breeds() = listOf(BreedDto(42, "Dorper"))
        override suspend fun animals(ranchId: String, page: Int, pageSize: Int, situation: String): AnimalPageDto {
            reads++
            return AnimalPageDto("ranch", "Rancho", allowed, pageItems.size, page, pageItems)
        }
        override suspend fun animal(id: String) = current
        override suspend fun create(ranchId: String, animal: JsonObject): Response<AnimalDetailDto> { writes++; body = animal; return response }
        override suspend fun update(id: String, animal: JsonObject): Response<AnimalDetailDto> { writes++; body = animal; return response }
    }
    private fun error(status: Int, json: String): Response<AnimalDetailDto> = Response.error(status, json.toResponseBody("application/json".toMediaType()))

    @Test fun createMapsCodesIsoDateCatalogIdAndStableClientId() = runBlocking {
        val api = Api()
        val repo = RemoteAnimalEditorRepository(api, { listOf(ranch) })
        val result = repo.save(null, valid.copy(destination = "Reemplazo", reproductiveState = "Vacía",
            birthType = "Cuádruple", conception = "Inseminación artificial (IA)"), null)
        val body = api.body!!
        assertEquals(valid.clientId, result.id)
        mapOf("id" to valid.clientId, "sexo" to "H", "categoria" to "vientre", "motivo_ingreso" to "registro_inicial",
            "fecha_nacimiento" to "2022-03-02", "destino" to "reemplazo", "estado_reproductivo" to "vacia",
            "tipo_parto_nacimiento" to "cuadruple", "metodo_concepcion" to "IA").forEach { (key, value) -> assertEquals(key, value, body[key].asString) }
        assertEquals(42, body["raza_id"].asInt)
        assertFalse(body.has("version"))
        assertFalse(body.has("photoPath"))
    }

    @Test fun editIncludesVersionAndExplicitNullsWithoutChangingAdmissionOrUnexposedFields() {
        val body = valid.copy(reason = null, mother = ParentChoice(), father = ParentChoice()).apiBody(42, 7)
        assertEquals(7, body["version"].asInt)
        listOf("siniiga", "registro_uno", "madre_id", "madre_externa", "padre_id", "padre_externo", "observaciones").forEach { assertTrue(it, body[it].isJsonNull) }
        listOf("id", "motivo_ingreso", "nombre", "estado_sanitario", "situacion", "photoPath").forEach { assertFalse(it, body.has(it)) }
    }

    @Test fun parentsRoundTripLocalExternalUnknownAndBirthLock() {
        val mapped = record.copy(motherId = "mother", fatherId = "external", fatherExternal = true,
            fatherFolio = "Rancho vecino", birthLocked = true).toRecord()
        assertEquals(ParentChoice(ParentKind.LOCAL, "mother"), mapped.form.mother)
        assertEquals(ParentChoice(ParentKind.EXTERNAL, reference = "Rancho vecino"), mapped.form.father)
        assertTrue(mapped.birthLocked)
        val body = mapped.form.apiBody(42, 2)
        assertEquals("mother", body["madre_id"].asString)
        assertTrue(body["madre_externa"].isJsonNull)
        assertTrue(body["padre_id"].isJsonNull)
        assertEquals("Rancho vecino", body["padre_externo"].asString)
    }

    @Test fun duplicateFolioSiniigaAndRegistrationBecomeFieldErrors() {
        mapOf("folio" to AnimalField.FOLIO, "siniiga" to AnimalField.SINIIGA, "registro_uno" to AnimalField.REGISTRATION).forEach { (field, expected) ->
            val failure = runCatching { error(409, """{"campo":"$field","detalle":"Dato duplicado"}""").animalOrThrow() }.exceptionOrNull()
            assertEquals(mapOf(expected to "Dato duplicado"), (failure as AnimalValidationException).fields)
        }
    }

    @Test fun pydantic422LocationsMapToSiniigaAndExternalParent() {
        val failure = runCatching { error(422, """{"detail":[{"loc":["body","siniiga"],"msg":"bad"},{"loc":["body","padre_externo"],"msg":"too long"}]}""").animalOrThrow() }.exceptionOrNull()
        assertEquals(setOf(AnimalField.SINIIGA, AnimalField.FATHER), (failure as AnimalValidationException).fields.keys)
    }

    @Test fun permissionBirthLockAndVersionConflictRemainDifferentErrors() {
        assertTrue(runCatching { error(403, """{"detail":"denied"}""").animalOrThrow() }.exceptionOrNull() is AnimalEditingDeniedException)
        assertTrue(runCatching { error(403, """{"codigo":"OV001"}""").animalOrThrow() }.exceptionOrNull() is AnimalBirthLockedException)
        assertTrue(runCatching { error(409, """{"detail":"version conflict"}""").animalOrThrow() }.exceptionOrNull() is AnimalEditConflictException)
        assertTrue(runCatching { error(422, """{"codigo":"DATOS_INVALIDOS"}""").animalOrThrow() }.exceptionOrNull() is AnimalServerValidationException)
    }

    @Test fun permissionsComeFromProfileAndServerAndAreRecheckedBeforeSaving() = runBlocking {
        val api = Api()
        var access = ranch
        val repo = RemoteAnimalEditorRepository(api, { listOf(access) })
        assertTrue(repo.loadEditor(null).canEdit)
        access = ranch.copy(canEdit = false)
        assertFalse(repo.loadEditor(null).canEdit)
        assertTrue(runCatching { repo.save(null, valid, null) }.exceptionOrNull() is AnimalEditingDeniedException)
        access = ranch
        api.allowed = false
        assertFalse(repo.loadEditor(null).canEdit)
        assertEquals(0, api.writes)
    }

    @Test fun newerVersionAndWrongRanchPreventWrites() = runBlocking {
        val api = Api()
        val repo = RemoteAnimalEditorRepository(api, { listOf(ranch) })
        assertTrue(runCatching { repo.save(record.id, valid, 1) }.exceptionOrNull() is AnimalEditConflictException)
        api.current = record.copy(ranchId = "other")
        assertTrue(runCatching { repo.loadEditor(record.id) }.isFailure)
        api.current = record
        api.pageItems = listOf(record.copy(ranchId = "other"))
        assertTrue(runCatching { repo.loadEditor(null) }.isFailure)
        assertEquals(0, api.writes)
    }

    @Test fun responseFromWrongRanchIsNotAcceptedAndPhotosAreScopedLocally() = runBlocking {
        val api = Api()
        val photos = mutableMapOf<Pair<String, String>, String?>()
        val repo = RemoteAnimalEditorRepository(api, { listOf(ranch) }, { ranchId, id -> photos[ranchId to id] }, { ranchId, id, path -> photos[ranchId to id] = path })
        repo.save(null, valid.copy(photoPath = "/local/photo.jpg"), null)
        assertEquals("/local/photo.jpg", repo.loadEditor(record.id).existing!!.form.photoPath)
        api.response = Response.success(record.copy(ranchId = "other"))
        assertTrue(runCatching { repo.save(null, valid, null) }.isFailure)
        assertEquals(1, photos.size)
    }
}
