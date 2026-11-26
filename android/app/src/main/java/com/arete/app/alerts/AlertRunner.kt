package com.arete.app.alerts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate

/** Muestra o quita una notificación. La implementación real es NotificationAlertPresenter. */
interface AlertPresenter {
    /** false si no se pudo mostrar (por ejemplo, el usuario no dio permiso). */
    fun show(alert: Alert): Boolean
    fun cancel(key: String)
}

/** Recuerda qué avisos se mostraron y en qué día, para no repetirlos de más. */
interface ShownAlertStore {
    fun shown(): Map<String, LocalDate>
    fun replace(shown: Map<String, LocalDate>)
}

/**
 * Un recorrido de la tarea diaria (CU-18): calcula los avisos de hoy, muestra los nuevos
 * y quita los que ya no aplican (parto registrado, cría destetada o dada de baja).
 * Los avisos que se repiten (parto no registrado) salen como máximo una vez al día.
 */
class AlertRunner(
    private val source: AlertDataSource,
    private val store: ShownAlertStore,
    private val presenter: AlertPresenter
) {
    // La tarea diaria y la de inicio de sesión pueden coincidir; el candado evita que
    // las dos lean el mismo historial y muestren un aviso dos veces.
    private val mutex = Mutex()

    /** Regresa los avisos que se mostraron en este recorrido. */
    suspend fun run(now: Instant = Instant.now()): List<Alert> = mutex.withLock {
        val input = source.load()
        if (input == null) {
            clearLocked()   // sin sesión no deben quedar avisos de un rancho a la vista
            return@withLock emptyList()
        }
        val today = now.atZone(input.zone).toLocalDate()
        val current = AlertRules.alertsFor(today, input.births, input.lambs, input.settings)
        val currentKeys = current.map { it.key }.toSet()
        val shown = store.shown()

        (shown.keys - currentKeys).forEach(presenter::cancel)
        val fresh = current
            .filter { alert -> isDue(alert, shown[alert.key], today) }
            .filter { presenter.show(it) }
        store.replace(shown.filterKeys { it in currentKeys } + fresh.associate { it.key to today })
        fresh
    }

    private fun isDue(alert: Alert, lastShown: LocalDate?, today: LocalDate): Boolean =
        lastShown == null || (alert.type.repeatsDaily && lastShown.isBefore(today))

    /** CA-21.4: al guardar el parto se quitan los avisos de esa gestación. Lo llama HU-26. */
    suspend fun cancelForBirth(eventId: String) = mutex.withLock {
        val keys = BIRTH_ALERTS.map { "${it.name}:$eventId" }.toSet()
        keys.forEach(presenter::cancel)
        store.replace(store.shown() - keys)
    }

    /** Quita todos los avisos (al cerrar sesión). */
    suspend fun clear() = mutex.withLock { clearLocked() }

    private fun clearLocked() {
        store.shown().keys.forEach(presenter::cancel)
        store.replace(emptyMap())
    }

    private companion object {
        val BIRTH_ALERTS = setOf(
            AlertType.MOVE_TO_BIRTH_PEN, AlertType.BIRTH_WINDOW_STARTED, AlertType.BIRTH_NOT_REGISTERED
        )
    }
}
