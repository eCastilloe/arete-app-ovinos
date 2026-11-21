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
import androidx.navigation.navArgument
import android.net.Uri
import com.arete.app.ui.screens.animal.AnimalEditorScreen
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
            HomeScreen(onInventory = { navController.navigate(AppRoutes.INVENTORY) { launchSingleTop = true } })
        }
        composable(AppRoutes.INVENTORY) { entry ->
            val refresh by entry.savedStateHandle.getStateFlow("inventoryRefresh", 0L).collectAsState()
            InventoryScreen(onHome = { navController.popBackStack(AppRoutes.HOME, false) },
                onAdd = { navController.navigate("animal-editor") },
                onEdit = { id -> navController.navigate("animal-editor?animalId=${Uri.encode(id)}") }, refreshKey = refresh)
        }
        composable("animal-editor?animalId={animalId}", arguments = listOf(navArgument("animalId") { defaultValue = "" })) {
            AnimalEditorScreen(onBack = { navController.popBackStack() }, onSaved = {
                navController.previousBackStackEntry?.savedStateHandle?.set("inventoryRefresh", System.nanoTime())
                navController.popBackStack()
            })
        }
    }

    LaunchedEffect(isLoggedIn) {
        val target = if (isLoggedIn) AppRoutes.HOME else AppRoutes.LOGIN
        val current = navController.currentDestination?.route
        if ((!isLoggedIn && current != AppRoutes.LOGIN) || (isLoggedIn && current == AppRoutes.LOGIN)) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
