package com.arete.app.reader

import kotlinx.coroutines.delay

/**
 * Lector de mentiras para el emulador, que no tiene NFC ni cámara útil.
 * Espera un poco, como un lector real, y siempre devuelve el animal de prueba.
 */
class SimulatedLectorArete(
    private val readDelayMillis: Long = DEFAULT_READ_DELAY_MILLIS
) : LectorArete {

    override val isAvailable: Boolean = true

    override suspend fun read(): ReadResult {
        delay(readDelayMillis)
        return ReadResult.Success(animalId = TEST_ANIMAL_ID)
    }

    companion object {
        /** UUID del animal de prueba. Debe coincidir con el animal semilla de HU-45. */
        const val TEST_ANIMAL_ID = "00000000-0000-4000-8000-000000000001"

        private const val DEFAULT_READ_DELAY_MILLIS = 600L
    }
}