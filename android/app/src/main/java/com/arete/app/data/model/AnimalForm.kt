package com.arete.app.data.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

val AnimalBreeds = listOf("Dorper", "Pelifolk", "Pelibuey", "Katahdin", "Blackbelly", "Suffolk", "Charollais", "Hampshire", "Dorset")
val BirthDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
fun parseBirthDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim(), BirthDateFormat) }.getOrNull()

enum class EntryReason(val label: String, val code: String) {
    INITIAL_REGISTRATION("Registro inicial", "registro_inicial"), PURCHASE("Compra", "compra"), RETURN("Reingreso", "reingreso"), TRANSFER("Transferencia", "transferencia")
}
enum class ParentKind { UNKNOWN, EXTERNAL, LOCAL }
data class ParentChoice(val kind: ParentKind = ParentKind.UNKNOWN, val id: String? = null, val reference: String = "")

data class AnimalForm(
    val folio: String = "", val siniiga: String = "", val breed: String = "", val color: String = "",
    val sex: AnimalSex = AnimalSex.FEMALE, val category: AnimalCategory = AnimalCategory.EWE,
    val reason: EntryReason? = null, val birthDate: String = "", val approximateDate: Boolean = false,
    val mother: ParentChoice = ParentChoice(), val father: ParentChoice = ParentChoice(),
    val birthType: String = "", val conception: String = "",
    val reproductiveState: String = "", val destination: String = "",
    val registration: String = "", val dna: String = "", val breeder: String = "", val owner: String = "",
    val photoPath: String? = null, val notes: String = "", val clientId: String? = null
) {
    fun normalized() = copy(folio = folio.trim(), siniiga = siniiga.trim(), color = color.trim(),
        birthDate = birthDate.trim(), registration = registration.trim(), dna = dna.trim(),
        breeder = breeder.trim(), owner = owner.trim(), notes = notes.trim(),
        mother = mother.copy(reference = mother.reference.trim()), father = father.copy(reference = father.reference.trim()),
        reproductiveState = if (sex == AnimalSex.FEMALE) reproductiveState else "")
}

enum class AnimalField(val step: Int) {
    FOLIO(0), SINIIGA(0), BREED(0), COLOR(0), SEX(0), CATEGORY(0),
    REASON(1), BIRTH_DATE(1), MOTHER(1), FATHER(1), BIRTH_TYPE(1), CONCEPTION(1),
    REPRODUCTIVE_STATE(2), DESTINATION(2), REGISTRATION(2), DNA(2), BREEDER(2), OWNER(2), PHOTO(2), NOTES(2)
}

data class AnimalRecord(
    val id: String, val ranchId: String, val form: AnimalForm, val version: Int = 1,
    val situation: AnimalSituation = AnimalSituation.ACTIVE, val lastWeightKg: Double? = null,
    val birthLocked: Boolean = false, val isExternal: Boolean = false, val isDeleted: Boolean = false,
    val lastTesticularCircumferenceCm: Double? = null
) {
    fun toInventory() = InventoryAnimal(id, ranchId, form.folio, form.siniiga.ifBlank { null },
        form.sex, form.category, situation, form.breed, form.reproductiveState.ifBlank { null },
        lastWeightKg, isExternal, isDeleted)
}

data class AnimalEditorContext(
    val ranchId: String, val ranchName: String, val canEdit: Boolean,
    val records: List<AnimalRecord>, val existing: AnimalRecord? = null,
    val breeds: List<String> = AnimalBreeds, val isDemo: Boolean = false,
    val breedIds: Map<String, Int> = emptyMap()
)

fun parentCandidates(context: AnimalEditorContext, sex: AnimalSex): List<AnimalRecord> = context.records.filter {
    val savedParent = if (sex == AnimalSex.FEMALE) context.existing?.form?.mother else context.existing?.form?.father
    it.ranchId == context.ranchId && it.form.sex == sex && it.id != context.existing?.id && !it.isDeleted && !it.isExternal &&
        (it.situation == AnimalSituation.ACTIVE || it.id == savedParent?.id)
}.sortedBy { it.form.folio }

fun validateAnimalForm(form: AnimalForm, context: AnimalEditorContext, today: LocalDate): Map<AnimalField, String> = buildMap {
    if (form.folio.isBlank()) put(AnimalField.FOLIO, "Escribe el folio del animal.")
    if (form.siniiga.isNotBlank() && !Regex("^484[0-9]{7,12}$").matches(form.siniiga.trim()))
        put(AnimalField.SINIIGA, "Debe empezar con 484 y tener entre 10 y 15 dígitos.")
    if (form.breed !in context.breeds) put(AnimalField.BREED, "Selecciona una raza del catálogo.")
    if (form.color.isBlank()) put(AnimalField.COLOR, "Escribe el color del animal.")
    if (form.reason == null && context.existing == null) put(AnimalField.REASON, "Selecciona el motivo de ingreso.")
    val date = parseBirthDate(form.birthDate)
    if (date == null || date > today) put(AnimalField.BIRTH_DATE, "Usa una fecha válida (dd/mm/aaaa), que no sea futura.")
    if ((form.category in listOf(AnimalCategory.EWE, AnimalCategory.REPLACEMENT) && form.sex != AnimalSex.FEMALE) ||
        (form.category == AnimalCategory.STUD && form.sex != AnimalSex.MALE))
        put(AnimalField.CATEGORY, "La categoría no corresponde al sexo del animal.")
    listOf(Triple(form.mother, AnimalSex.FEMALE, AnimalField.MOTHER), Triple(form.father, AnimalSex.MALE, AnimalField.FATHER)).forEach { (choice, sex, field) ->
        when (choice.kind) {
            ParentKind.UNKNOWN -> Unit
            ParentKind.EXTERNAL -> if (choice.reference.isBlank() || choice.reference.length > 30) put(field, "Escribe una referencia externa de 1 a 30 caracteres.")
            ParentKind.LOCAL -> {
                val parent = parentCandidates(context, sex).find { it.id == choice.id }
                val parentDate = parent?.form?.birthDate?.let(::parseBirthDate)
                if (parent == null) put(field, "Selecciona un progenitor válido de este rancho.")
                else if (date != null && parentDate != null && parentDate >= date)
                    put(field, "El progenitor debe haber nacido antes que este animal.")
            }
        }
    }
    context.existing?.takeIf { it.birthLocked }?.let { old ->
        if (form.birthDate != old.form.birthDate || form.approximateDate != old.form.approximateDate)
            put(AnimalField.BIRTH_DATE, "Nacimiento bloqueado: solicita la corrección a la Asociación.")
        if (form.mother != old.form.mother) put(AnimalField.MOTHER, "La madre está vinculada a un parto bloqueado.")
        if (form.sex != old.form.sex) put(AnimalField.SEX, "El sexo está vinculado a un parto bloqueado.")
        if (form.birthType != old.form.birthType) put(AnimalField.BIRTH_TYPE, "El tipo de parto está bloqueado.")
    }
}
