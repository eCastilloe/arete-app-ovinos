package com.arete.app.alerts

import java.time.LocalDate

/** Parámetros del rancho que usan los avisos (tabla parametros_rancho, docs/03). */
data class AlertSettings(
    val noticeDaysBeforeBirth: Int = 7,   // aviso_parto_dias_antes
    val weaningDays: Int = 75             // dias_alerta_destete: 60, 75 o 90 (RN-10)
) {
    init {
        require(noticeDaysBeforeBirth in 1..30)
        require(weaningDays in WEANING_OPTIONS)
    }

    companion object {
        val WEANING_OPTIONS = setOf(60, 75, 90)
    }
}

/**
 * Gestación abierta con su ventana de parto (como v_partos_esperados): evento en curso,
 * preñez no descartada y hembra activa. Cuando se registra el parto deja de llegar.
 */
data class ExpectedBirth(
    val eventId: String,
    val eweId: String,
    val folio: String,
    val windowStart: LocalDate,           // fpp_desde
    val windowEnd: LocalDate              // fpp_hasta
)

/** Cría del rancho (como v_destetes_pendientes, antes de filtrar por edad). */
data class SucklingLamb(
    val lambId: String,
    val folio: String,
    val birthDate: LocalDate,
    val isActive: Boolean,                // false si está muerta, vendida o de desecho
    val isWeaned: Boolean
)

enum class AlertType(val repeatsDaily: Boolean) {
    MOVE_TO_BIRTH_PEN(repeatsDaily = false),
    // RF-21: aviso al inicio de la ventana de parto; dura mientras la ventana sigue abierta.
    BIRTH_WINDOW_STARTED(repeatsDaily = false),
    // El más urgente: se repite una vez al día hasta que se registre el parto.
    BIRTH_NOT_REGISTERED(repeatsDaily = true),
    WEANING_DUE(repeatsDaily = false)
}

data class Alert(
    val type: AlertType,
    val sourceId: String,                 // eventId para partos, lambId para destetes
    val animalId: String,
    val folio: String,
    val title: String,
    val body: String
) {
    /** Identifica el aviso para no repetirlo y para cancelarlo (CA-21.4). */
    val key: String get() = "${type.name}:$sourceId"
}
