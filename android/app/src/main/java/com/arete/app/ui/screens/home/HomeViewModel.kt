package com.arete.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arete.app.AreteApplication
import com.arete.app.data.repository.AuthRepository
import com.arete.app.data.repository.SyncRepository
import com.arete.app.reader.LectorArete
import com.arete.app.reader.ReadResult
import com.arete.app.reader.ReaderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authRepository: AuthRepository,
    syncRepository: SyncRepository,
    private val lectorArete: LectorArete
) : ViewModel() {

    val pendingCount: StateFlow<Int> = syncRepository.pendingCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    private val _readerState = MutableStateFlow<ReaderState>(ReaderState.Idle)
    val readerState: StateFlow<ReaderState> = _readerState.asStateFlow()

    fun readTag() {
        if (_readerState.value is ReaderState.Reading) return

        if (!lectorArete.isAvailable) {
            _readerState.value = ReaderState.Error("Este celular no puede leer aretes")
            return
        }

        _readerState.value = ReaderState.Reading
        viewModelScope.launch {
            _readerState.value = when (val result = lectorArete.read()) {
                is ReadResult.Success -> ReaderState.Found(result.animalId)
                is ReadResult.Failure -> ReaderState.Error(result.message)
            }
        }
    }

    /** La pantalla avisa que ya abrió la ficha, para no volver a navegar al regresar. */
    fun onAnimalOpened() {
        _readerState.value = ReaderState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AreteApplication
                HomeViewModel(
                    authRepository = app.container.authRepository,
                    syncRepository = app.container.syncRepository,
                    lectorArete = app.container.lectorArete
                )
            }
        }
    }
}