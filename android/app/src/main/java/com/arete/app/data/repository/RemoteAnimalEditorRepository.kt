package com.arete.app.data.repository

import com.arete.app.data.model.*
import com.arete.app.data.remote.AnimalApiService
import com.arete.app.data.remote.dto.*
import com.google.gson.JsonParser
import retrofit2.HttpException
import retrofit2.Response
import java.time.LocalDate
import java.time.ZoneId

class AnimalBirthLockedException : Exception()
class AnimalServerValidationException : Exception()

class RemoteAnimalEditorRepository(
    private val api: AnimalApiService,
    private val ranches: suspend () -> List<RanchAccessDto>,
    private val readPhoto: (String, String) -> String? = { _, _ -> null },
    private val savePhoto: (String, String, String?) -> Unit = { _, _, _ -> }
) : AnimalEditorRepository {
    override suspend fun loadEditor(animalId: String?): AnimalEditorContext {
        val ranch = ranches().firstOrNull() ?: throw NoAssignedRanchException()
        if (!ranch.canEdit) return AnimalEditorContext(ranch.ranchId, ranch.name, false, emptyList(), breeds = emptyList())
        val existing = animalId?.let { api.animal(it).toRecord() }
        check(existing == null || (existing.ranchId == ranch.ranchId && !existing.isExternal))
        val breeds = api.breeds()
        check(breeds.isNotEmpty() && breeds.map { it.name }.distinct().size == breeds.size)
        val records = mutableListOf<AnimalRecord>()
        var page = 1
        do {
            val result = api.animals(ranch.ranchId, page)
            check(result.ranchId == ranch.ranchId && result.page == page && result.items.all { it.ranchId == ranch.ranchId })
            if (!result.canEdit) return AnimalEditorContext(ranch.ranchId, ranch.name, false, emptyList(), breeds = emptyList())
            records += result.items.map { it.toRecord() }
            check(records.size <= result.total && records.map { it.id }.distinct().size == records.size)
            if (records.size == result.total) break
            check(result.items.isNotEmpty())
            page++
        } while (true)
        val withPhoto = existing?.let { it.copy(form = it.form.copy(photoPath = readPhoto(it.ranchId, it.id))) }
        return AnimalEditorContext(ranch.ranchId, ranch.name, true, records, withPhoto,
            breeds = breeds.map { it.name }, breedIds = breeds.associate { it.name to it.id })
    }

    override suspend fun save(animalId: String?, form: AnimalForm, expectedVersion: Int?): AnimalRecord {
        // Reload permissions/catalogue before writing; the API remains authoritative.
        val context = loadEditor(animalId)
        if (!context.canEdit) throw AnimalEditingDeniedException()
        if (animalId != null && (expectedVersion == null || context.existing?.version != expectedVersion)) throw AnimalEditConflictException()
        val errors = validateAnimalForm(form, context, LocalDate.now(ZoneId.of("America/Mexico_City")))
        if (errors.isNotEmpty()) throw AnimalValidationException(errors)
        val body = form.apiBody(context.breedIds.getValue(form.breed), expectedVersion)
        val response = if (animalId == null) api.create(context.ranchId, body) else api.update(animalId, body)
        val result = response.animalOrThrow().toRecord()
        check(result.ranchId == context.ranchId && result.id == (animalId ?: form.clientId))
        savePhoto(result.ranchId, result.id, form.photoPath)
        return result.copy(form = result.form.copy(photoPath = form.photoPath))
    }
}

internal fun Response<AnimalDetailDto>.animalOrThrow(): AnimalDetailDto {
    if (isSuccessful) return requireNotNull(body())
    val json = runCatching { JsonParser.parseString(errorBody()?.string()).asJsonObject }.getOrNull()
    fun text(name: String) = json?.get(name)?.takeIf { it.isJsonPrimitive }?.asString
    if (code() == 403) {
        if (text("codigo") == "OV001") throw AnimalBirthLockedException()
        throw AnimalEditingDeniedException()
    }
    val fields = mutableMapOf<AnimalField, String>()
    animalApiFields[text("campo")]?.let { fields[it] = text("detalle") ?: "Revisa este dato." }
    if (code() == 422) {
        json?.get("detail")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            val error = item.asJsonObject
            val field = error.getAsJsonArray("loc")?.mapNotNull { part ->
                part.takeIf { it.isJsonPrimitive }?.asString?.let(animalApiFields::get)
            }?.lastOrNull()
            if (field != null) fields[field] = when (field) {
                AnimalField.SINIIGA -> "Debe empezar con 484 y tener entre 10 y 15 dígitos."
                AnimalField.MOTHER, AnimalField.FATHER -> "Revisa el progenitor; la referencia externa admite hasta 30 caracteres."
                AnimalField.BIRTH_DATE -> "Usa una fecha válida, que no sea futura."
                else -> "El servidor no acepta este dato. Revisa su formato y longitud."
            }
        }
    }
    if (fields.isNotEmpty() && code() in listOf(409, 422)) throw AnimalValidationException(fields)
    if (code() == 409) throw AnimalEditConflictException()
    if (code() == 422) throw AnimalServerValidationException()
    throw HttpException(this)
}

private val animalApiFields = mapOf(
    "folio" to AnimalField.FOLIO, "siniiga" to AnimalField.SINIIGA, "raza_id" to AnimalField.BREED,
    "color" to AnimalField.COLOR, "sexo" to AnimalField.SEX, "categoria" to AnimalField.CATEGORY,
    "motivo_ingreso" to AnimalField.REASON, "fecha_nacimiento" to AnimalField.BIRTH_DATE,
    "fecha_nacimiento_aprox" to AnimalField.BIRTH_DATE, "madre_id" to AnimalField.MOTHER,
    "madre_externa" to AnimalField.MOTHER, "padre_id" to AnimalField.FATHER, "padre_externo" to AnimalField.FATHER,
    "tipo_parto_nacimiento" to AnimalField.BIRTH_TYPE, "metodo_concepcion" to AnimalField.CONCEPTION,
    "estado_reproductivo" to AnimalField.REPRODUCTIVE_STATE, "destino" to AnimalField.DESTINATION,
    "registro_uno" to AnimalField.REGISTRATION, "adn" to AnimalField.DNA, "criador" to AnimalField.BREEDER,
    "propietario_actual" to AnimalField.OWNER, "observaciones" to AnimalField.NOTES
)
