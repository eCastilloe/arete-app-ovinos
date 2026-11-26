package com.arete.app.alerts

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Decide qué avisos tocan hoy (HU-39, RF-11, RF-21, CU-18). Es lógica pura: no lee
 * la BD ni el reloj, así que se prueba con JVM y funciona igual sin internet.
 */
object AlertRules {
    private val dayMonth = DateTimeFormatter.ofPattern("dd/MM")

    fun alertsFor(
        today: LocalDate,
        births: List<ExpectedBirth>,
        lambs: List<SucklingLamb>,
        settings: AlertSettings
    ): List<Alert> = births.mapNotNull { birthAlert(today, it, settings) } +
        lambs.mapNotNull { weaningAlert(today, it, settings) }

    private fun birthAlert(today: LocalDate, birth: ExpectedBirth, settings: AlertSettings): Alert? {
        val window = "Ventana de parto del ${birth.windowStart.format(dayMonth)} al ${birth.windowEnd.format(dayMonth)}"
        return when {
            // CA-21.5: la ventana terminó y el parto sigue sin registrarse.
            today.isAfter(birth.windowEnd) -> Alert(
                AlertType.BIRTH_NOT_REGISTERED, birth.eventId, birth.eweId, birth.folio,
                title = "Parto no registrado: revisar o avisar al veterinario",
                body = "Folio ${birth.folio} · $window"
            )
            // RF-21: la ventana ya empezó y todavía no termina. El texto no viene en el SRS;
            // se tomó de la propuesta de la revisión de HU-39.
            !today.isBefore(birth.windowStart) -> Alert(
                AlertType.BIRTH_WINDOW_STARTED, birth.eventId, birth.eweId, birth.folio,
                title = "Empieza la ventana de parto: folio ${birth.folio}",
                body = "Puede parir entre el ${birth.windowStart.format(dayMonth)} y el " +
                    "${birth.windowEnd.format(dayMonth)}; revisa el corral de parto"
            )
            // CA-21.3: desde N días antes del inicio de la ventana, hasta que empieza.
            !today.isBefore(birth.windowStart.minusDays(settings.noticeDaysBeforeBirth.toLong())) &&
                today.isBefore(birth.windowStart) -> Alert(
                AlertType.MOVE_TO_BIRTH_PEN, birth.eventId, birth.eweId, birth.folio,
                title = "Mover a corral de parto: folio ${birth.folio}",
                body = window
            )
            else -> null
        }
    }

    // CA-11.1 a CA-11.3: cría activa, sin destetar, que ya cumplió los días configurados.
    private fun weaningAlert(today: LocalDate, lamb: SucklingLamb, settings: AlertSettings): Alert? {
        if (!lamb.isActive || lamb.isWeaned) return null
        val ageDays = ChronoUnit.DAYS.between(lamb.birthDate, today)
        if (ageDays < settings.weaningDays) return null
        return Alert(
            AlertType.WEANING_DUE, lamb.lambId, lamb.lambId, lamb.folio,
            title = "Destete pendiente: folio ${lamb.folio}",
            body = "Tiene $ageDays días; el destete es a los ${settings.weaningDays}"
        )
    }
}
