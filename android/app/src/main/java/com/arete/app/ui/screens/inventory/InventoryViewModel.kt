package com.arete.app.ui.screens.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arete.app.AreteApplication
import com.arete.app.data.model.InventoryCategory
import com.arete.app.data.model.InventorySnapshot
import com.arete.app.data.repository.InventoryRepository
import com.arete.app.data.repository.InventoryUnavailableException
import com.arete.app.data.repository.NoAssignedRanchException
import com.arete.app.data.repository.IncompleteInventoryException
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InventoryViewModel(
    private val repository: InventoryRepository,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private var snapshot: InventorySnapshot? = null
    private var filters = InventoryFilters(
        category = InventoryCategory.entries.firstOrNull { it.name == savedState.get<String>("category") }
            ?: InventoryCategory.FEMALES,
        query = savedState["query"] ?: "",
        showDischarged = savedState["discharged"] ?: false
    )
    private var loading: Job? = null
    private val mutableState = MutableStateFlow(InventoryUiState(filters = filters))
    val state: StateFlow<InventoryUiState> = mutableState.asStateFlow()

    init { retry() }

    fun retry() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            snapshot = null
            mutableState.value = InventoryUiState(filters = filters)
            mutableState.value = try {
                val result = withContext(Dispatchers.IO) { repository.load() }
                snapshot = result
                projectInventory(result, filters)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoAssignedRanchException) {
                InventoryUiState(isLoading = false, filters = filters, error = "No tienes ranchos asignados")
            } catch (e: IncompleteInventoryException) {
                InventoryUiState(isLoading = false, filters = filters, error = "No pudimos cargar todos los animales de tu rancho. Intenta de nuevo más tarde.")
            } catch (e: HttpException) {
                InventoryUiState(isLoading = false, filters = filters, error = when (e.code()) {
                    401 -> "Tu sesión venció. Vuelve a entrar."
                    403, 404 -> "No tienes acceso al inventario de este rancho."
                    else -> "No pudimos cargar el inventario. Intenta de nuevo más tarde."
                })
            } catch (e: InventoryUnavailableException) {
                InventoryUiState(isLoading = false, filters = filters, error = "El inventario de tu rancho aún no está disponible. Intenta de nuevo más tarde.")
            } catch (e: Exception) {
                InventoryUiState(isLoading = false, filters = filters, error = "No pudimos cargar el inventario. Revisa tu conexión e intenta de nuevo.")
            }
        }
    }

    fun search(query: String) = update(filters.copy(query = query))
    fun selectCategory(category: InventoryCategory) = update(InventoryFilters(category = category))
    fun toggleDischarged() = update(filters.copy(showDischarged = !filters.showDischarged))

    private fun update(value: InventoryFilters) {
        savedState["category"] = value.category.name
        savedState["query"] = value.query
        savedState["discharged"] = value.showDischarged
        filters = value
        // A bounded local list (1,000 animals) is filtered immediately. This also
        // returns each TextField edit synchronously, without lost/late keystrokes.
        mutableState.value = snapshot?.let { projectInventory(it, value) }
            ?: mutableState.value.copy(filters = value)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AreteApplication
                InventoryViewModel(app.container.inventoryRepository, createSavedStateHandle())
            }
        }
    }
}
