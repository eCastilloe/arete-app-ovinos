package com.arete.app.ui.screens.animal

import android.net.Uri
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arete.app.AreteApplication
import com.arete.app.data.local.AnimalPhotoStore
import com.arete.app.data.model.*
import com.arete.app.data.repository.*
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class AnimalEditorState(
    val loading: Boolean = true, val context: AnimalEditorContext? = null,
    val form: AnimalForm = AnimalForm(), val step: Int = 0, val dirty: Boolean = false,
    val saving: Boolean = false, val photoBusy: Boolean = false,
    val errors: Map<AnimalField, String> = emptyMap(), val error: String? = null,
    val savedId: String? = null, val validationAttempt: Int = 0
)

class AnimalEditorViewModel(
    private val repository: AnimalEditorRepository,
    private val photos: AnimalPhotoStore? = null,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("America/Mexico_City")) }
) : ViewModel() {
    private val gson = Gson()
    private val animalId = savedState.get<String>("animalId")?.ifBlank { null }
    private val mutableState = MutableStateFlow(AnimalEditorState())
    val state = mutableState.asStateFlow()

    init { load() }

    fun load() {
        if (state.value.context != null || state.value.saving) return
        mutableState.value = state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val context = withContext(Dispatchers.IO) { repository.loadEditor(animalId) }
                val draft = savedState.get<String>("draft")?.let { runCatching { gson.fromJson(it, AnimalForm::class.java) }.getOrNull() }
                mutableState.value = AnimalEditorState(loading = false, context = context,
                    form = draft ?: context.existing?.form ?: AnimalForm(clientId = java.util.UUID.randomUUID().toString()), dirty = draft != null,
                    step = (savedState.get<Int>("step") ?: 0).coerceIn(0, 2))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.value = state.value.copy(loading = false,
                    error = "La ficha de tu rancho aún no está disponible. Intenta de nuevo más tarde.")
            }
        }
    }

    fun change(field: AnimalField, transform: (AnimalForm) -> AnimalForm) {
        val current = state.value
        if (current.context?.canEdit != true || current.saving || current.photoBusy) return
        val form = transform(current.form)
        savedState["draft"] = gson.toJson(form)
        mutableState.value = current.copy(form = form, dirty = true, errors = current.errors - field, error = null)
    }

    fun sex(sex: AnimalSex) = change(AnimalField.SEX) { form ->
        if (sex == form.sex) form else form.copy(sex = sex,
            category = if (sex == AnimalSex.FEMALE) AnimalCategory.EWE else AnimalCategory.STUD,
            reproductiveState = "", destination = "")
    }

    fun step(value: Int) {
        if (state.value.saving) return
        savedState["step"] = value.coerceIn(0, 2)
        mutableState.value = state.value.copy(step = value.coerceIn(0, 2))
    }

    fun photoError() {
        mutableState.value = state.value.copy(errors = state.value.errors + (AnimalField.PHOTO to "No pudimos abrir la cámara o la foto. Puedes elegir otra imagen."))
    }

    fun importPhoto(uri: Uri) {
        if (state.value.context?.canEdit != true || state.value.saving || state.value.photoBusy || photos == null) return
        mutableState.value = state.value.copy(photoBusy = true)
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) { photos.import(uri) }
                mutableState.value = state.value.copy(photoBusy = false)
                change(AnimalField.PHOTO) { it.copy(photoPath = path) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.value = state.value.copy(photoBusy = false)
                photoError()
            }
        }
    }

    fun save() {
        val current = state.value
        val context = current.context ?: return
        if (!context.canEdit || current.saving || current.photoBusy || current.savedId != null) return
        val form = current.form.normalized()
        val errors = validateAnimalForm(form, context, today())
        if (errors.isNotEmpty()) { fieldErrors(errors); return }
        mutableState.value = current.copy(saving = true, errors = emptyMap(), error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.save(animalId, form, context.existing?.version) }
                savedState.remove<String>("draft")
                mutableState.value = state.value.copy(saving = false, dirty = false, savedId = result.id)
            } catch (e: CancellationException) { throw e
            } catch (e: AnimalValidationException) { fieldErrors(e.fields)
            } catch (e: AnimalBirthLockedException) {
                mutableState.value = state.value.copy(saving = false, error = "Los datos de nacimiento están bloqueados. Solicita la corrección a la Asociación; conservamos lo escrito.")
            } catch (e: AnimalServerValidationException) {
                mutableState.value = state.value.copy(saving = false, error = "El servidor rechazó los datos. Revisa la categoría, el sexo y los progenitores; conservamos lo escrito.")
            } catch (e: AnimalEditingDeniedException) {
                mutableState.value = state.value.copy(saving = false, context = context.copy(canEdit = false),
                    error = "Ya no tienes permiso para editar. No se guardó la ficha.")
            } catch (e: AnimalEditConflictException) {
                mutableState.value = state.value.copy(saving = false,
                    error = "La ficha cambió mientras la editabas. Conservamos lo escrito; copia tus cambios y vuelve a abrirla.")
            } catch (e: Exception) {
                mutableState.value = state.value.copy(saving = false,
                    error = "No pudimos guardar. Conservamos tus datos; intenta de nuevo.")
            }
        }
    }

    private fun fieldErrors(errors: Map<AnimalField, String>) {
        val step = errors.keys.minOfOrNull { it.step } ?: state.value.step
        savedState["step"] = step
        mutableState.value = state.value.copy(saving = false, errors = errors, step = step,
            error = "Revisa los campos marcados.", validationAttempt = state.value.validationAttempt + 1)
    }

    companion object {
        val Factory = viewModelFactory { initializer {
            val app = this[APPLICATION_KEY] as AreteApplication
            AnimalEditorViewModel(app.container.animalEditorRepository, app.container.animalPhotoStore, createSavedStateHandle())
        } }
    }
}
