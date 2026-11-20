package com.arete.app.ui.navigation

object AppRoutes {
    const val LOGIN = "login"
    const val HOME = "home"

    // Provisional (HU-53). HU-19 conserva esta ruta para la ficha real.
    const val ARG_ANIMAL_ID = "animalId"
    const val ANIMAL_DETAIL = "animal/{$ARG_ANIMAL_ID}"

    fun animalDetail(animalId: String): String = "animal/$animalId"
}