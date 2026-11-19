package com.arete.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arete.app.ui.screens.animal.AnimalDetailScreen
import com.arete.app.ui.screens.home.HomeScreen
import com.arete.app.ui.screens.login.LoginScreen

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
                }
            )
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
            val target = if (isLoggedIn) AppRoutes.HOME else AppRoutes.LOGIN
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}