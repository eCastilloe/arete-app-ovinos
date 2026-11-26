package com.arete.app.alerts

import com.arete.app.data.local.TokenStorage
import java.time.LocalDate
import java.time.ZoneId

// Este source set no entra en release. Los avisos demo no tocan ningún rancho real.
object AlertDataSourceFactory {
    fun create(tokenStorage: TokenStorage): AlertDataSource = AlertDataSource {
        // Cuenta real: la fuente llega con HU-37 (ventanas de parto); mientras, sin avisos.
        if (tokenStorage.getSession()?.isDemo == true) DemoAlerts.input() else null
    }
}

/** Avisos de ejemplo relativos a hoy, con folios del inventario demo (HU-18). */
object DemoAlerts {
    fun input(): AlertInput {
        val settings = AlertSettings()
        val zone = ZoneId.of("America/Mexico_City")
        val today = LocalDate.now(zone)
        return AlertInput(
            ranchId = "rancho-de-ejemplo",
            births = listOf(
                // Su ventana empieza en 7 días → "Mover a corral de parto".
                ExpectedBirth("demo-evento-1", "animal-0", "AIR-2253", today.plusDays(7), today.plusDays(11)),
                // Su ventana empezó ayer → "Empieza la ventana de parto".
                ExpectedBirth("demo-evento-3", "animal-2", "H-3", today.minusDays(1), today.plusDays(3)),
                // Su ventana terminó hace 6 días → "Parto no registrado".
                ExpectedBirth("demo-evento-2", "animal-1", "H-2", today.minusDays(10), today.minusDays(6))
            ),
            lambs = listOf(
                SucklingLamb("animal-20000", "C-1", today.minusDays(75), isActive = true, isWeaned = false),
                SucklingLamb("animal-20003", "C-4", today.minusDays(80), isActive = true, isWeaned = true),
                SucklingLamb("animal-20006", "C-7", today.minusDays(80), isActive = false, isWeaned = false)
            ),
            settings = settings,
            zone = zone
        )
    }
}
