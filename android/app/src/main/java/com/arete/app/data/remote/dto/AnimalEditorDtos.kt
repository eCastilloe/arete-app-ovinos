package com.arete.app.data.remote.dto

import com.arete.app.data.model.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.time.LocalDate

data class BreedDto(@SerializedName("id") val id: Int, @SerializedName("nombre") val name: String)
data class AnimalPageDto(
    @SerializedName("rancho_id") val ranchId: String,
    @SerializedName("rancho_nombre") val ranchName: String,
    @SerializedName("puede_editar") val canEdit: Boolean,
    @SerializedName("total") val total: Int,
    @SerializedName("pagina") val page: Int,
    @SerializedName("items") val items: List<AnimalDetailDto>
)

data class AnimalDetailDto(
    @SerializedName("id") val id: String,
    @SerializedName("rancho_id") val ranchId: String,
    @SerializedName("folio") val folio: String,
    @SerializedName("sexo") val sex: String,
    @SerializedName("categoria") val category: String,
    @SerializedName("version") val version: Int,
    @SerializedName("siniiga") val siniiga: String? = null,
    @SerializedName("raza") val breed: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("fecha_nacimiento") val birthDate: String? = null,
    @SerializedName("fecha_nacimiento_aprox") val approximateDate: Boolean = false,
    @SerializedName("motivo_ingreso") val reason: String? = null,
    @SerializedName("madre_id") val motherId: String? = null,
    @SerializedName("madre_folio") val motherFolio: String? = null,
    @SerializedName("madre_es_externa") val motherExternal: Boolean = false,
    @SerializedName("padre_id") val fatherId: String? = null,
    @SerializedName("padre_folio") val fatherFolio: String? = null,
    @SerializedName("padre_es_externo") val fatherExternal: Boolean = false,
    @SerializedName("tipo_parto_nacimiento") val birthType: String? = null,
    @SerializedName("metodo_concepcion") val conception: String? = null,
    @SerializedName("estado_reproductivo") val reproductiveState: String? = null,
    @SerializedName("destino") val destination: String? = null,
    @SerializedName("registro_uno") val registration: String? = null,
    @SerializedName("adn") val dna: String? = null,
    @SerializedName("criador") val breeder: String? = null,
    @SerializedName("propietario_actual") val owner: String? = null,
    @SerializedName("observaciones") val notes: String? = null,
    @SerializedName("nacimiento_bloqueado") val birthLocked: Boolean = false,
    @SerializedName("situacion") val situation: String = "activo",
    @SerializedName("es_externo") val isExternal: Boolean = false,
    @SerializedName("ultimo_peso_kg") val lastWeightKg: Double? = null
) {
    fun toRecord(): AnimalRecord {
        val inventory = InventoryAnimalDto(id, ranchId, folio, siniiga, sex, category, situation,
            breed, reproductiveState, lastWeightKg, isExternal).toInventoryAnimal()
        return AnimalRecord(id, ranchId, AnimalForm(
            clientId = id, folio = folio, siniiga = siniiga.orEmpty(), breed = breed.orEmpty(), color = color.orEmpty(),
            sex = inventory.sex, category = inventory.category, reason = EntryReason.entries.find { it.code == reason },
            birthDate = birthDate?.let { LocalDate.parse(it).format(BirthDateFormat) }.orEmpty(), approximateDate = approximateDate,
            mother = parent(motherId, motherFolio, motherExternal), father = parent(fatherId, fatherFolio, fatherExternal),
            birthType = AnimalCodes.birthTypes.label(birthType), conception = AnimalCodes.conceptions.label(conception),
            reproductiveState = AnimalCodes.reproductiveStates.label(reproductiveState), destination = AnimalCodes.destinations.label(destination),
            registration = registration.orEmpty(), dna = dna.orEmpty(), breeder = breeder.orEmpty(), owner = owner.orEmpty(), notes = notes.orEmpty()
        ), version, inventory.situation, lastWeightKg, birthLocked, isExternal)
    }
    private fun parent(id: String?, folio: String?, external: Boolean) = when {
        id == null -> ParentChoice()
        external -> ParentChoice(ParentKind.EXTERNAL, reference = folio.orEmpty())
        else -> ParentChoice(ParentKind.LOCAL, id)
    }
}

object AnimalCodes {
    val categories = mapOf(AnimalCategory.EWE to "vientre", AnimalCategory.STUD to "semental",
        AnimalCategory.SUCKLING to "cria_lactante", AnimalCategory.FATTENING to "cordero_engorda", AnimalCategory.REPLACEMENT to "cordera_recria")
    val birthTypes = linkedMapOf("Simple" to "simple", "Gemelar" to "gemelar", "Triple" to "triple", "Cuádruple" to "cuadruple", "Quíntuple" to "quintuple")
    val conceptions = linkedMapOf("Monta natural (MN)" to "MN", "Inseminación artificial (IA)" to "IA", "Transferencia embrionaria (TE)" to "TE")
    val reproductiveStates = linkedMapOf("Vacía" to "vacia", "En empadre" to "empadre", "Gestante" to "gestante", "Lactancia" to "lactancia")
    val destinations = linkedMapOf("Pie de cría" to "pie_de_cria", "Engorda" to "engorda", "Rastro" to "rastro", "Reemplazo" to "reemplazo", "Venta" to "venta")
}
private fun Map<String, String>.label(code: String?) = if (code == null) "" else entries.first { it.value == code }.key

/** Only fields exposed by this form are written; other API fields are preserved. */
data class AnimalWriteDto(
    @SerializedName("folio") val folio: String,
    @SerializedName("siniiga") val siniiga: String?,
    @SerializedName("raza_id") val breedId: Int,
    @SerializedName("color") val color: String,
    @SerializedName("sexo") val sex: String,
    @SerializedName("categoria") val category: String,
    @SerializedName("fecha_nacimiento") val birthDate: String,
    @SerializedName("fecha_nacimiento_aprox") val approximateDate: Boolean,
    @SerializedName("madre_id") val motherId: String?,
    @SerializedName("madre_externa") val motherExternal: String?,
    @SerializedName("padre_id") val fatherId: String?,
    @SerializedName("padre_externo") val fatherExternal: String?,
    @SerializedName("tipo_parto_nacimiento") val birthType: String?,
    @SerializedName("metodo_concepcion") val conception: String?,
    @SerializedName("estado_reproductivo") val reproductiveState: String?,
    @SerializedName("destino") val destination: String?,
    @SerializedName("registro_uno") val registration: String?,
    @SerializedName("adn") val dna: String?,
    @SerializedName("criador") val breeder: String?,
    @SerializedName("propietario_actual") val owner: String?,
    @SerializedName("observaciones") val notes: String?
)

fun AnimalForm.apiBody(breedId: Int, expectedVersion: Int?): JsonObject {
    val form = normalized()
    fun String.optional() = ifBlank { null }
    fun ParentChoice.local() = id.takeIf { kind == ParentKind.LOCAL }
    fun ParentChoice.external() = reference.takeIf { kind == ParentKind.EXTERNAL }
    val dto = with(form) { AnimalWriteDto(folio, siniiga.optional(), breedId, color,
        if (sex == AnimalSex.FEMALE) "H" else "M", AnimalCodes.categories.getValue(category),
        requireNotNull(parseBirthDate(birthDate)).toString(), approximateDate,
        mother.local(), mother.external(), father.local(), father.external(),
        AnimalCodes.birthTypes[birthType], AnimalCodes.conceptions[conception],
        AnimalCodes.reproductiveStates[reproductiveState], AnimalCodes.destinations[destination],
        registration.optional(), dna.optional(), breeder.optional(), owner.optional(), notes.optional()) }
    // PATCH needs explicit nulls to clear a previously saved optional value/parent.
    return GsonBuilder().serializeNulls().create().toJsonTree(dto).asJsonObject.apply {
        if (expectedVersion == null) {
            addProperty("id", requireNotNull(form.clientId))
            addProperty("motivo_ingreso", requireNotNull(form.reason).code)
        } else addProperty("version", expectedVersion)
    }
}
