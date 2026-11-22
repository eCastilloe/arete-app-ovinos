package com.arete.app.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lógica de una lectura de arete, lista para reutilizar.
 * Cada ViewModel crea uno con su `viewModelScope`; así ninguna pantalla copia esta lógica.
 */
class TagReader(
    private val lectorArete: LectorArete,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Idle)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    /** Inicia una lectura. Devuelve su `Job` (útil en pruebas) o `null` si no se inició. */
    fun readTag(): Job? {
        if (_state.value is ReaderState.Reading) return null

        if (!lectorArete.isAvailable) {
            _state.value = ReaderState.Error(UNAVAILABLE_MESSAGE)
            return null
        }

        _state.value = ReaderState.Reading
        return scope.launch {
            _state.value = when (val result = lectorArete.read()) {
                is ReadResult.Success -> ReaderState.Found(result.animalId)
                is ReadResult.Failure -> ReaderState.Error(result.message)
            }
        }
    }

    /** La pantalla avisa que ya abrió la ficha, para no volver a navegar al regresar. */
    fun onAnimalOpened() {
        _state.value = ReaderState.Idle
    }

    companion object {
        const val UNAVAILABLE_MESSAGE = "Este celular no puede leer aretes"
    }
}