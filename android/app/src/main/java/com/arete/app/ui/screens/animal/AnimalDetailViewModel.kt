package com.arete.app.ui.screens.animal

import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arete.app.AreteApplication
import com.arete.app.data.model.AnimalDetail
import com.arete.app.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class AnimalDetailState(
    val loading: Boolean = true, val detail: AnimalDetail? = null, val error: String? = null,
    val onDate: LocalDate = LocalDate.now(ZoneId.of("America/Mexico_City"))
)

class AnimalDetailViewModel(
    private val repository: AnimalDetailRepository,
    savedState: SavedStateHandle,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("America/Mexico_City")) }
) : ViewModel() {
    private val animalId = savedState.get<String>("animalId").orEmpty()
    private val mutableState = MutableStateFlow(AnimalDetailState(onDate = today()))
    val state = mutableState.asStateFlow()
    private var loadingJob: Job? = null

    init { reload() }

    fun reload() {
        loadingJob?.cancel()
        // Clear old ranch data while rechecking access, including after returning from editing.
        mutableState.value = AnimalDetailState(onDate = today())
        loadingJob = viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.IO) { repository.loadDetail(animalId) }
                mutableState.value = AnimalDetailState(loading = false, detail = detail, onDate = today())
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.value = AnimalDetailState(loading = false, onDate = today(), error =
                    if (e is AnimalDetailNotFoundException) "Esta ficha no está disponible en tu rancho."
                    else "No pudimos cargar la ficha de tu rancho. Intenta de nuevo.")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer {
            val app = this[APPLICATION_KEY] as AreteApplication
            AnimalDetailViewModel(app.container.animalDetailRepository, createSavedStateHandle())
        } }
    }
}
