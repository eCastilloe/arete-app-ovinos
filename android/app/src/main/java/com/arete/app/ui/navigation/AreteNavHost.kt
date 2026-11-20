package com.arete.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    val startDestination = remember { if (isLoggedIn) AppRoutes.INVENTORY else AppRoutes.LOGIN }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppRoutes.LOGIN) { LoginScreen() }
        composable(AppRoutes.HOME) {
            HomeScreen(onInventory = { navController.navigate(AppRoutes.INVENTORY) { launchSingleTop = true } })
        }
        composable(AppRoutes.INVENTORY) {
            InventoryScreen(onHome = {
                navController.navigate(AppRoutes.HOME) {
                    popUpTo(AppRoutes.INVENTORY) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
    }

    LaunchedEffect(isLoggedIn) {
        val target = if (isLoggedIn) AppRoutes.INVENTORY else AppRoutes.LOGIN
        val current = navController.currentDestination?.route
        if ((!isLoggedIn && current != AppRoutes.LOGIN) || (isLoggedIn && current == AppRoutes.LOGIN)) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
