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
    val startDestination = remember { if (isLoggedIn) AppRoutes.INVENTORY else AppRoutes.LOGIN }

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
            InventoryScreen(onHome = { navController.navigate(AppRoutes.HOME) { popUpTo(AppRoutes.INVENTORY) { inclusive = true }; launchSingleTop = true } },
                onAdd = { navController.navigate(AppRoutes.animalEditor()) },
                onEdit = { id -> navController.navigate(AppRoutes.animalEditor(id)) }, refreshKey = refresh)
        }
        composable(AppRoutes.ANIMAL_EDITOR, arguments = listOf(navArgument("animalId") { defaultValue = "" })) {
            AnimalEditorScreen(onBack = { navController.popBackStack() }, onSaved = {
                navController.previousBackStackEntry?.savedStateHandle?.set("inventoryRefresh", System.nanoTime())
                navController.popBackStack()
            })
        }
        composable(
            route = AppRoutes.ANIMAL_DETAIL,
            arguments = listOf(navArgument(AppRoutes.ARG_ANIMAL_ID) { type = NavType.StringType })
        ) { entry ->
            AnimalDetailScreen(
                animalId = entry.arguments?.getString(AppRoutes.ARG_ANIMAL_ID).orEmpty(),
                onBack = { navController.popBackStack() }
            )
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
            val target = if (isLoggedIn) AppRoutes.INVENTORY else AppRoutes.LOGIN
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
