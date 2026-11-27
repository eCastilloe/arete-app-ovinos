package com.arete.app.alerts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AlertRunnerTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val birth = ExpectedBirth("evento-1", "hembra-1", "287",
        LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 30))
    private val lamb = SucklingLamb("cria-1", "C-12", LocalDate.of(2026, 9, 5), isActive = true, isWeaned = false)

    private class FakePresenter(var allowed: Boolean = true) : AlertPresenter {
        val visible = mutableSetOf<String>()
        val shownCount = mutableMapOf<String, Int>()
        override fun show(alert: Alert): Boolean {
            if (!allowed) return false
            visible += alert.key
            shownCount[alert.key] = (shownCount[alert.key] ?: 0) + 1
            return true
        }
        override fun cancel(key: String) { visible -= key }
    }

    private class MemoryStore : ShownAlertStore {
        var entries = emptyMap<String, LocalDate>()
        val keys get() = entries.keys
        override fun shown() = entries
        override fun replace(shown: Map<String, LocalDate>) { entries = shown }
    }

    private var input: AlertInput? = AlertInput("rancho-1", listOf(birth), listOf(lamb), AlertSettings(), zone)
    private val presenter = FakePresenter()
    private val store = MemoryStore()
    private val runner = AlertRunner({ input }, store, presenter)

    /** Las 7:00 de ese día en Jalisco. */
    private fun at(date: LocalDate) = date.atTime(7, 0).atZone(zone).toInstant()

    @Test fun `muestra los avisos de hoy`() = runBlocking {
        // 19 nov: mover a corral (ventana 26 nov) y destete (cría de 75 días)
        val shown = runner.run(at(LocalDate.of(2026, 11, 19)))
        assertEquals(setOf("MOVE_TO_BIRTH_PEN:evento-1", "WEANING_DUE:cria-1"), shown.map { it.key }.toSet())
        assertEquals(shown.map { it.key }.toSet(), presenter.visible)
    }

    @Test fun `no repite un aviso al dia siguiente`() = runBlocking {
        runner.run(at(LocalDate.of(2026, 11, 19)))
        val second = runner.run(at(LocalDate.of(2026, 11, 20)))
        assertTrue(second.isEmpty())
        assertEquals(1, presenter.shownCount["MOVE_TO_BIRTH_PEN:evento-1"])
    }

    @Test fun `parto no registrado se repite una vez al dia`() = runBlocking {
        val day1 = LocalDate.of(2026, 12, 1)
        runner.run(at(day1))
        // Se vuelve a abrir la app el mismo día: no se repite.
        assertTrue(runner.run(day1.atTime(15, 0).atZone(zone).toInstant()).none { it.type == AlertType.BIRTH_NOT_REGISTERED })
        // Al día siguiente sí, y otra vez al siguiente.
        assertTrue(runner.run(at(day1.plusDays(1))).any { it.key == "BIRTH_NOT_REGISTERED:evento-1" })
        runner.run(at(day1.plusDays(2)))
        assertEquals(3, presenter.shownCount["BIRTH_NOT_REGISTERED:evento-1"])
        assertEquals(1, presenter.shownCount["WEANING_DUE:cria-1"])   // el destete no se repite
    }

    @Test fun `parto registrado deja de repetir el aviso de atraso`() = runBlocking {
        runner.run(at(LocalDate.of(2026, 12, 1)))
        input = input!!.copy(births = emptyList())   // ya no llega la gestación: se registró el parto
        runner.run(at(LocalDate.of(2026, 12, 2)))
        assertEquals(1, presenter.shownCount["BIRTH_NOT_REGISTERED:evento-1"])
        assertTrue("BIRTH_NOT_REGISTERED:evento-1" !in presenter.visible)
    }

    @Test fun `al empezar la ventana el aviso de mover se cambia por el de ventana`() = runBlocking {
        runner.run(at(LocalDate.of(2026, 11, 19)))
        val shown = runner.run(at(LocalDate.of(2026, 11, 26)))
        assertTrue("MOVE_TO_BIRTH_PEN:evento-1" !in presenter.visible)
        assertTrue("MOVE_TO_BIRTH_PEN:evento-1" !in store.keys)
        assertEquals(listOf("BIRTH_WINDOW_STARTED:evento-1"), shown.map { it.key })
    }

    @Test fun `cancelar el parto tambien quita el aviso de ventana`() = runBlocking {
        runner.run(at(LocalDate.of(2026, 11, 27)))
        assertTrue("BIRTH_WINDOW_STARTED:evento-1" in presenter.visible)
        runner.cancelForBirth("evento-1")
        assertTrue("BIRTH_WINDOW_STARTED:evento-1" !in presenter.visible)
    }

    @Test fun `dos recorridos al mismo tiempo no duplican avisos`() = runBlocking {
        // La tarea diaria y la de inicio de sesión pueden coincidir.
        val day = at(LocalDate.of(2026, 11, 19))
        listOf(async(Dispatchers.Default) { runner.run(day) }, async(Dispatchers.Default) { runner.run(day) }).awaitAll()
        assertEquals(1, presenter.shownCount["MOVE_TO_BIRTH_PEN:evento-1"])
        assertEquals(1, presenter.shownCount["WEANING_DUE:cria-1"])
    }

    @Test fun `parto registrado cancela los avisos de esa gestacion`() = runBlocking {  // CA-21.4
        runner.run(at(LocalDate.of(2026, 12, 1)))
        assertTrue("BIRTH_NOT_REGISTERED:evento-1" in presenter.visible)
        runner.cancelForBirth("evento-1")
        assertTrue("BIRTH_NOT_REGISTERED:evento-1" !in presenter.visible)
        assertTrue("WEANING_DUE:cria-1" in presenter.visible)   // el destete no se toca
    }

    @Test fun `cria destetada quita su aviso en el siguiente recorrido`() = runBlocking {  // CA-11.3
        runner.run(at(LocalDate.of(2026, 11, 19)))
        input = input!!.copy(lambs = listOf(lamb.copy(isWeaned = true)))
        runner.run(at(LocalDate.of(2026, 11, 20)))
        assertTrue("WEANING_DUE:cria-1" !in presenter.visible)
    }

    @Test fun `sin permiso no marca el aviso y lo muestra cuando se concede`() = runBlocking {
        presenter.allowed = false
        assertTrue(runner.run(at(LocalDate.of(2026, 11, 19))).isEmpty())
        assertTrue(store.keys.isEmpty())
        presenter.allowed = true
        assertEquals(2, runner.run(at(LocalDate.of(2026, 11, 20))).size)
    }

    @Test fun `sin sesion se quitan todos los avisos`() = runBlocking {
        runner.run(at(LocalDate.of(2026, 11, 19)))
        input = null
        runner.run(at(LocalDate.of(2026, 11, 20)))
        assertTrue(presenter.visible.isEmpty())
        assertTrue(store.keys.isEmpty())
    }

    @Test fun `el dia se calcula con la hora de Jalisco`() = runBlocking {
        // 19 nov 23:30 en Jalisco ya es 20 nov en UTC; debe contar como 19 nov.
        val lateNight = LocalDate.of(2026, 11, 19).atTime(23, 30).atZone(zone).toInstant()
        input = input!!.copy(births = listOf(birth.copy(windowStart = LocalDate.of(2026, 11, 27))), lambs = emptyList())
        // Ventana el 27 nov: el aviso empieza el 20 nov, así que el 19 nov (Jalisco) no avisa.
        assertTrue(runner.run(lateNight).isEmpty())
    }
}
