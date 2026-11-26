package com.arete.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.net.Uri
import com.arete.app.ui.screens.animal.AnimalEditorScreen
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arete.app.ui.screens.animal.AnimalDetailScreen
import com.arete.app.ui.screens.home.HomeScreen
import com.arete.app.ui.screens.login.LoginScreen
import com.arete.app.ui.screens.inventory.InventoryScreen

@Composable
fun AreteNavHost(isLoggedIn: Boolean?) {
    if (isLoggedIn == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    val navController = rememberNavController()
    val startDestination = remember { if (isLoggedIn) AppRoutes.HOME else AppRoutes.LOGIN }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppRoutes.LOGIN) { LoginScreen() }
        composable(AppRoutes.HOME) {
            HomeScreen(
                onAnimalRead = { animalId ->
                    navController.navigate(AppRoutes.animalDetail(animalId))
                },
                onInventory = { navController.navigate(AppRoutes.INVENTORY) { launchSingleTop = true } }
            )
        }
        composable(AppRoutes.INVENTORY) { entry ->
            val refresh by entry.savedStateHandle.getStateFlow("inventoryRefresh", 0L).collectAsState()
            InventoryScreen(onHome = { navController.popBackStack(AppRoutes.HOME, false) },
                onAdd = { navController.navigate("animal-editor") },
                onOpen = { id -> navController.navigate(AppRoutes.animalDetail(Uri.encode(id))) }, refreshKey = refresh)
        }
        // Ficha del animal (HU-20). La abren el inventario y "Leer arete" (HU-53) por la misma ruta.
        composable(
            route = AppRoutes.ANIMAL_DETAIL,
            arguments = listOf(navArgument(AppRoutes.ARG_ANIMAL_ID) { type = NavType.StringType })
        ) { entry ->
            val refresh by entry.savedStateHandle.getStateFlow("detailRefresh", 0L).collectAsState()
            AnimalDetailScreen(onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(AppRoutes.HOME, false) },
                onEdit = { id -> navController.navigate("animal-editor?animalId=${Uri.encode(id)}") }, refreshKey = refresh)
        }
        composable("animal-editor?animalId={animalId}", arguments = listOf(navArgument("animalId") { defaultValue = "" })) {
            val backLabel = remember {
                if (navController.previousBackStackEntry?.destination?.route == AppRoutes.ANIMAL_DETAIL)
                    "Volver a la ficha" else "Volver al inventario"
            }
            AnimalEditorScreen(onBack = { navController.popBackStack() }, onSaved = {
                navController.getBackStackEntry(AppRoutes.INVENTORY).savedStateHandle["inventoryRefresh"] = System.nanoTime()
                navController.previousBackStackEntry?.savedStateHandle?.set("detailRefresh", System.nanoTime())
                navController.popBackStack()
            }, backLabel = backLabel)
        }
    }

    // Guardia de sesión: solo actúa si la pantalla actual no corresponde a la sesión.
    // Con sesión, cualquier pantalla menos el login es válida (inicio, ficha, …).
    LaunchedEffect(isLoggedIn) {
        val currentRoute = navController.currentDestination?.route
        val needsRedirect = if (isLoggedIn) {
            currentRoute == AppRoutes.LOGIN
        } else {
            currentRoute != AppRoutes.LOGIN
        }
        if (needsRedirect) {
            val target = if (isLoggedIn) AppRoutes.HOME else AppRoutes.LOGIN
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
