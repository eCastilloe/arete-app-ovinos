package com.arete.app.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TagReaderTest {

    @Test
    fun unavailableReaderShowsErrorWithoutReading() = runBlocking<Unit> {
        val lector = FakeLectorArete(isAvailable = false)
        val tagReader = TagReader(lector, this)

        val job = tagReader.readTag()

        assertNull(job)
        assertEquals(ReaderState.Error(TagReader.UNAVAILABLE_MESSAGE), tagReader.state.value)
        assertEquals(0, lector.readCount)
    }

    @Test
    fun successfulReadIsFoundThenIdleAfterOpening() = runBlocking<Unit> {
        val tagReader = TagReader(FakeLectorArete(), this)

        tagReader.readTag()?.join()
        assertEquals(ReaderState.Found(ANIMAL_ID), tagReader.state.value)

        tagReader.onAnimalOpened()
        assertEquals(ReaderState.Idle, tagReader.state.value)
    }

    @Test
    fun failedReadShowsItsMessage() = runBlocking<Unit> {
        val lector = FakeLectorArete(result = ReadResult.Failure("Arete no reconocido"))
        val tagReader = TagReader(lector, this)

        tagReader.readTag()?.join()

        assertEquals(ReaderState.Error("Arete no reconocido"), tagReader.state.value)
    }

    @Test
    fun secondTapWhileReadingIsIgnored() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val lector = FakeLectorArete(gate = gate)
        val tagReader = TagReader(lector, this)

        val firstJob = tagReader.readTag()
        assertEquals(ReaderState.Reading, tagReader.state.value)

        val secondJob = tagReader.readTag()
        assertNull(secondJob)

        gate.complete(Unit)
        assertNotNull(firstJob)
        firstJob?.join()
        assertEquals(1, lector.readCount)
    }

    private class FakeLectorArete(
        override val isAvailable: Boolean = true,
        private val result: ReadResult = ReadResult.Success(ANIMAL_ID),
        private val gate: CompletableDeferred<Unit>? = null
    ) : LectorArete {

        var readCount = 0
            private set

        override suspend fun read(): ReadResult {
            readCount++
            gate?.await()
            return result
        }
    }

    private companion object {
        const val ANIMAL_ID = "11111111-1111-4111-8111-111111111111"
    }
}