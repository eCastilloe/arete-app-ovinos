package com.arete.app.reader

/**
 * Contrato único para leer el arete de un animal (CA-N03.4).
 *
 * Las pantallas y los ViewModels solo conocen esta interfaz. La implementación
 * concreta (simulada hoy, NFC o QR en HU-54) se elige en un solo lugar:
 * `di/AppContainer`.
 */
interface LectorArete {

    /** `false` si este celular no puede leer con esta implementación (por ejemplo, sin NFC). */
    val isAvailable: Boolean

    /** Espera una lectura y devuelve el `id` (UUID) del animal o el motivo del fallo. */
    suspend fun read(): ReadResult
}

sealed interface ReadResult {
    data class Success(val animalId: String) : ReadResult
    data class Failure(val message: String) : ReadResult
}