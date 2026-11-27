package com.arete.app.animal

import com.arete.app.data.model.*
import com.arete.app.data.remote.AnimalApiService
import com.arete.app.data.remote.dto.*
import com.arete.app.data.repository.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import java.time.LocalDate

class RemoteAnimalDetailTest {
    private val ranch = RanchAccessDto("r1", "Rancho 1", "encargado", "asignado", false, false, false, false)
    private val dto = AnimalDetailDto("a1", "r1", "79", "H", "vientre", 1,
        motherId = "mother", motherFolio = "45", fatherId = "father", fatherFolio = "S-100", fatherExternal = true,
        reason = "nacimiento", birthWeightKg = 3.2)
    private val requests = mutableListOf<String>()
    private fun repository(record: AnimalDetailDto = dto, code: Int = 200, ranches: List<RanchAccessDto> = listOf(ranch)): AnimalDetailRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.encodedPath
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(Gson().toJson(record).toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.invalid/").client(client)
            .addConverterFactory(AnimalApiService.converterFactory()).build().create(AnimalApiService::class.java)
        return RemoteAnimalDetailRepository(api, { ranches }, { r, id -> "$r/$id.jpg" })
    }
    @Test fun readsRealDetailForReadOnlyUserWithoutCallingEditorOrFetchingAllAnimals() = runBlocking {
        val detail = repository().loadDetail("a1")
        assertFalse(detail.canEdit)
        assertFalse(detail.isDemo)
        assertEquals(listOf("/animales/a1"), requests)
        assertEquals("45", detail.motherLabel)
        assertEquals("Externo · S-100", detail.fatherLabel)
        assertEquals("Nacimiento", detail.entryReasonLabel)
        assertEquals("r1/a1.jpg", detail.animal.form.photoPath)
        assertEquals(3.2, detail.displayWeightKg)
        assertTrue(detail.usesBirthWeight)
    }
    @Test fun measuredWeightTakesPrecedenceOverBirthWeight() = runBlocking {
        val detail = repository(dto.copy(lastWeightKg = 17.0)).loadDetail("a1")
        assertEquals(17.0, detail.displayWeightKg)
        assertFalse(detail.usesBirthWeight)
    }
    @Test fun missingOptionalFieldsRemainUnknownForCurrentServer() = runBlocking {
        val detail = repository(dto.copy(birthWeightKg = null)).loadDetail("a1")
        assertNull(detail.displayWeightKg)
        assertNull(detail.animal.lastTesticularCircumferenceCm)
        assertNull(animalAge(parseBirthDate(detail.animal.form.birthDate), LocalDate.now()))
    }
    @Test fun maleMeasurementAndDateComeFromApi() = runBlocking {
        val detail = repository(dto.copy(sex = "M", category = "semental", circumferenceCm = 32.0, circumferenceDate = "2026-11-20")).loadDetail("a1")
        assertEquals(32.0, detail.animal.lastTesticularCircumferenceCm)
        assertEquals("2026-11-20", detail.circumferenceDate)
    }
    @Test fun rejectsForeignExternalMismatchedAndMissingAnimals() = runBlocking {
        listOf(dto.copy(ranchId = "r2"), dto.copy(id = "other"), dto.copy(isExternal = true)).forEach {
            try { repository(it).loadDetail("a1"); fail("Unauthorized animal") } catch (_: AnimalDetailNotFoundException) { }
        }
        listOf(403, 404).forEach {
            try { repository(code = it).loadDetail("a1"); fail("Unavailable animal") } catch (_: AnimalDetailNotFoundException) { }
        }
        try { repository(ranches = emptyList()).loadDetail("a1"); fail("No ranch") } catch (_: AnimalDetailNotFoundException) { }
    }
    @Test fun serverFailureIsNotReplacedWithDemoData() = runBlocking {
        try { repository(code = 500).loadDetail("a1"); fail("Expected API error") } catch (e: HttpException) { assertEquals(500, e.code()) }
    }
    @Test fun dischargedAnimalStillReadableAndPermissionRechecked() = runBlocking {
        val detail = repository(dto.copy(situation = "vendido"), ranches = listOf(ranch.copy(canEdit = true))).loadDetail("a1")
        assertEquals(AnimalSituation.SOLD, detail.animal.situation)
        assertTrue(detail.canEdit)
    }
    @Test fun leapBirthdayAndMonthEndUseCompletedCalendarMonths() {
        assertEquals(AnimalAge(0, 28), animalAge(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)))
        assertEquals(AnimalAge(1, 1), animalAge(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1)))
        assertEquals(AnimalAge(11, 30), animalAge(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28)))
        assertEquals(AnimalAge(48, 0), animalAge(LocalDate.of(2024, 2, 29), LocalDate.of(2028, 2, 29)))
    }
}
