package com.arete.app.reader

/**
 * Estado de una lectura de arete, para mostrarlo en cualquier pantalla
 * (inicio, inventario, asistente de parto…).
 */
sealed interface ReaderState {
    data object Idle : ReaderState
    data object Reading : ReaderState
    data class Found(val animalId: String) : ReaderState
    data class Error(val message: String) : ReaderState
}