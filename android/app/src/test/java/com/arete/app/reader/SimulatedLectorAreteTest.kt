package com.arete.app.reader

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedLectorAreteTest {

    @Test
    fun isAlwaysAvailable() {
        val reader: LectorArete = SimulatedLectorArete()

        assertTrue(reader.isAvailable)
    }

    @Test
    fun readReturnsTestAnimal() = runBlocking {
        val reader: LectorArete = SimulatedLectorArete(readDelayMillis = 0)

        val result = reader.read()

        assertEquals(ReadResult.Success(SimulatedLectorArete.TEST_ANIMAL_ID), result)
    }
}