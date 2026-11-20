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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.arete.app.AreteApplication
import com.arete.app.data.model.RanchAccess
import com.arete.app.ui.screens.team.TeamScreen
import androidx.navigation.navArgument
import android.net.Uri
import com.arete.app.ui.screens.animal.AnimalEditorScreen
import com.arete.app.ui.screens.animal.AnimalDetailScreen
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
    val application = LocalContext.current.applicationContext as AreteApplication
    val access by application.container.teamRepository.access.collectAsState()
    var previousAccess by remember { mutableStateOf<RanchAccess?>(null) }
    val startDestination = remember { if (isLoggedIn) AppRoutes.HOME else AppRoutes.LOGIN }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppRoutes.LOGIN) { LoginScreen() }
        composable(AppRoutes.HOME) {
            HomeScreen(onInventory = { navController.navigate(AppRoutes.INVENTORY) { launchSingleTop = true } },
                onTeam = { navController.navigate("team") { launchSingleTop = true } })
        }
        composable("team") { TeamScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.INVENTORY) { entry ->
            val refresh by entry.savedStateHandle.getStateFlow("inventoryRefresh", 0L).collectAsState()
            InventoryScreen(onHome = { navController.popBackStack(AppRoutes.HOME, false) },
                onAdd = { navController.navigate("animal-editor") },
                onOpen = { id -> navController.navigate("animal-detail/${Uri.encode(id)}") }, refreshKey = refresh)
        }
        composable("animal-detail/{animalId}") { entry ->
            val refresh by entry.savedStateHandle.getStateFlow("detailRefresh", 0L).collectAsState()
            AnimalDetailScreen(onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(AppRoutes.HOME, false) },
                onEdit = { id -> navController.navigate("animal-editor?animalId=${Uri.encode(id)}") }, refreshKey = refresh)
        }
        composable("animal-editor?animalId={animalId}", arguments = listOf(navArgument("animalId") { defaultValue = "" })) {
            val backLabel = remember {
                if (navController.previousBackStackEntry?.destination?.route == "animal-detail/{animalId}")
                    "Volver a la ficha" else "Volver al inventario"
            }
            AnimalEditorScreen(onBack = { navController.popBackStack() }, onSaved = {
                navController.getBackStackEntry(AppRoutes.INVENTORY).savedStateHandle["inventoryRefresh"] = System.nanoTime()
                navController.previousBackStackEntry?.savedStateHandle?.set("detailRefresh", System.nanoTime())
                navController.popBackStack()
            }, backLabel = backLabel)
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
    LaunchedEffect(access) {
        val route = navController.currentDestination?.route
        if (previousAccess != null && previousAccess != access && route != AppRoutes.HOME && route != AppRoutes.LOGIN)
            navController.popBackStack(AppRoutes.HOME, false)
        previousAccess = access
    }
}
