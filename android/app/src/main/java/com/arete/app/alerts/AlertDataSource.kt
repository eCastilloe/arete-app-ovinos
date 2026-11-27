package com.arete.app.alerts

import java.time.ZoneId

/** Lo que necesitan las reglas para un rancho: gestaciones abiertas, crías y parámetros. */
data class AlertInput(
    val ranchId: String,
    val births: List<ExpectedBirth>,
    val lambs: List<SucklingLamb>,
    val settings: AlertSettings,
    val zone: ZoneId = ZoneId.of("America/Mexico_City")   // parametros_rancho.zona_horaria
)

/**
 * De dónde salen los datos de los avisos. Las pantallas y el worker solo conocen esta
 * interfaz (igual que LectorArete), así que cambiar la fuente no toca las reglas.
 *
 * Fuente real pendiente: la ventana de parto viene de los eventos reproductivos (HU-37)
 * y la fecha de nacimiento de las crías todavía no llega a la app.
 */
fun interface AlertDataSource {
    /** null cuando no hay sesión o no hay datos: no se muestra ningún aviso. */
    suspend fun load(): AlertInput?
}

class UnavailableAlertDataSource : AlertDataSource {
    override suspend fun load(): AlertInput? = null
}
