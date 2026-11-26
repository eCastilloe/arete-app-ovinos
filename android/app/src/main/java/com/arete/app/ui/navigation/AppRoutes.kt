package com.arete.app.ui.navigation

object AppRoutes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val INVENTORY = "inventory"
    // Ficha del animal (HU-20). La usan el inventario y "Leer arete" (HU-53).
    const val ARG_ANIMAL_ID = "animalId"
    const val ANIMAL_DETAIL = "animal/{$ARG_ANIMAL_ID}"

    fun animalDetail(animalId: String): String = "animal/$animalId"
}
