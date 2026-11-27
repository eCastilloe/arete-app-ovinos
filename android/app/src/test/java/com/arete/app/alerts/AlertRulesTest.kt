package com.arete.app.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlertRulesTest {
    private val defaults = AlertSettings()
    // Ventana del ejemplo de la tarjeta: inicia el 26 nov (CA-21.3); IA el 1 jul → 26–30 nov (CA-21.1).
    private val birth = ExpectedBirth("evento-1", "hembra-1", "287",
        LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 30))

    private fun birthAlerts(today: LocalDate, settings: AlertSettings = defaults) =
        AlertRules.alertsFor(today, listOf(birth), emptyList(), settings)

    private fun lamb(birthDate: LocalDate, active: Boolean = true, weaned: Boolean = false) =
        SucklingLamb("cria-1", "C-12", birthDate, active, weaned)

    private fun weaningAlerts(today: LocalDate, lamb: SucklingLamb, days: Int = 75) =
        AlertRules.alertsFor(today, emptyList(), listOf(lamb), AlertSettings(weaningDays = days))

    // ---------- Parto próximo (CA-21.3) ----------

    @Test fun `el 19 nov avisa mover a corral de parto`() {
        val alerts = birthAlerts(LocalDate.of(2026, 11, 19))
        assertEquals(1, alerts.size)
        assertEquals(AlertType.MOVE_TO_BIRTH_PEN, alerts[0].type)
        assertEquals("Mover a corral de parto: folio 287", alerts[0].title)
        assertEquals("hembra-1", alerts[0].animalId)
    }

    @Test fun `el 18 nov todavia no avisa`() {
        assertTrue(birthAlerts(LocalDate.of(2026, 11, 18)).isEmpty())
    }

    @Test fun `sigue avisando hasta el dia antes de la ventana`() {
        assertEquals(AlertType.MOVE_TO_BIRTH_PEN, birthAlerts(LocalDate.of(2026, 11, 25)).single().type)
    }

    // ---------- Inicio de la ventana (RF-21) ----------

    @Test fun `el 26 nov avisa que empezo la ventana de parto`() {
        val alert = birthAlerts(LocalDate.of(2026, 11, 26)).single()
        assertEquals(AlertType.BIRTH_WINDOW_STARTED, alert.type)
        assertEquals("Empieza la ventana de parto: folio 287", alert.title)
        assertEquals("Puede parir entre el 26/11 y el 30/11; revisa el corral de parto", alert.body)
    }

    @Test fun `el aviso de ventana dura hasta el ultimo dia de la ventana`() {
        assertEquals(AlertType.BIRTH_WINDOW_STARTED, birthAlerts(LocalDate.of(2026, 11, 30)).single().type)
        assertEquals(AlertType.BIRTH_NOT_REGISTERED, birthAlerts(LocalDate.of(2026, 12, 1)).single().type)
    }

    @Test fun `respeta los dias de aviso del rancho`() {
        val tenDays = AlertSettings(noticeDaysBeforeBirth = 10)
        assertEquals(1, birthAlerts(LocalDate.of(2026, 11, 16), tenDays).size)
        assertTrue(birthAlerts(LocalDate.of(2026, 11, 15), tenDays).isEmpty())
    }

    // ---------- Parto no registrado (CA-21.5) ----------

    @Test fun `al dia siguiente de la ventana avisa parto no registrado`() {
        val alert = birthAlerts(LocalDate.of(2026, 12, 1)).single()
        assertEquals(AlertType.BIRTH_NOT_REGISTERED, alert.type)
        assertEquals("Parto no registrado: revisar o avisar al veterinario", alert.title)
        assertTrue(alert.body.contains("Folio 287"))
    }

    @Test fun `parto registrado ya no genera avisos`() {  // CA-21.4: la gestación deja de llegar
        assertTrue(AlertRules.alertsFor(LocalDate.of(2026, 12, 1), emptyList(), emptyList(), defaults).isEmpty())
    }

    @Test fun `los avisos de una gestacion comparten el evento para cancelarlos`() {
        val alerts = listOf(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 1))
            .map { birthAlerts(it).single() }
        assertTrue(alerts.all { it.sourceId == "evento-1" })
        assertEquals(3, alerts.map { it.key }.toSet().size)
    }

    // ---------- Destete (CA-11.1 a CA-11.3) ----------

    @Test fun `cria que cumple 75 dias hoy avisa destete pendiente`() {  // CA-11.1
        val today = LocalDate.of(2026, 11, 26)
        val alert = weaningAlerts(today, lamb(today.minusDays(75))).single()
        assertEquals(AlertType.WEANING_DUE, alert.type)
        assertEquals("Destete pendiente: folio C-12", alert.title)
    }

    @Test fun `con 74 dias todavia no avisa`() {
        val today = LocalDate.of(2026, 11, 26)
        assertTrue(weaningAlerts(today, lamb(today.minusDays(74))).isEmpty())
    }

    @Test fun `rancho configurado a 60 o 90 dias`() {  // CA-11.2
        val today = LocalDate.of(2026, 11, 26)
        assertEquals(1, weaningAlerts(today, lamb(today.minusDays(60)), days = 60).size)
        assertTrue(weaningAlerts(today, lamb(today.minusDays(89)), days = 90).isEmpty())
        assertEquals(1, weaningAlerts(today, lamb(today.minusDays(90)), days = 90).size)
    }

    @Test fun `cria destetada o dada de baja no avisa`() {  // CA-11.3
        val today = LocalDate.of(2026, 11, 26)
        assertTrue(weaningAlerts(today, lamb(today.minusDays(80), weaned = true)).isEmpty())
        assertTrue(weaningAlerts(today, lamb(today.minusDays(80), active = false)).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `solo se aceptan 60, 75 o 90 dias de destete`() {
        AlertSettings(weaningDays = 70)
    }
}
