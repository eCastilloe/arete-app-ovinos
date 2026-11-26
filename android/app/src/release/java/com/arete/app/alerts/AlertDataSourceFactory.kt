package com.arete.app.alerts

import com.arete.app.data.local.TokenStorage

object AlertDataSourceFactory {
    // La fuente real llega con HU-37 (ventanas de parto); mientras, release no muestra avisos.
    @Suppress("UNUSED_PARAMETER")
    fun create(tokenStorage: TokenStorage): AlertDataSource = UnavailableAlertDataSource()
}
