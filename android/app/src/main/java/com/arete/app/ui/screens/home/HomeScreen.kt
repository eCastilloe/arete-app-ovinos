package com.arete.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arete.app.ui.components.*
import com.arete.app.ui.screens.team.DemoTeamControls
import com.arete.app.data.model.RanchRole

@Composable
fun HomeScreen(onInventory: () -> Unit = {}, onTeam: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val pending by viewModel.pendingCount.collectAsState()
    val access by viewModel.access.collectAsState()
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Arete", style = MaterialTheme.typography.headlineLarge)
                access?.let {
                    Text(it.ranchName, style = MaterialTheme.typography.titleMedium)
                    Text("${it.userName} · ${when (it.role) {
                        RanchRole.PRODUCER -> "Productor"
                        RanchRole.WORKER -> "Encargado"
                        RanchRole.ASSOCIATION -> "Asociación / Técnico"
                    }}", style = MaterialTheme.typography.bodyLarge)
                    Text(if (it.isDemo) "Modo de prueba · datos locales" else when (pending) {
                        0 -> "Al día"
                        1 -> "1 registro pendiente por subir"
                        else -> "$pending registros pendientes por subir"
                    }, style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SexChip(Sex.FEMALE); SexChip(Sex.MALE)
                }
                if (access == null) {
                    Text("Los permisos de tu rancho aún no están disponibles.", style = MaterialTheme.typography.bodyLarge)
                    AreteSecondaryButton("Consultar permisos", viewModel::refreshAccess)
                }
                RanchActions(access, onInventory, onTeam)
                if (access?.role == RanchRole.WORKER && access?.permissions?.read == false)
                    Text("No tienes permiso de lectura del inventario. Solicítalo al productor.", style = MaterialTheme.typography.bodyLarge)
                DemoTeamControls(viewModel.teamRepository)
                AreteSecondaryButton("Cerrar sesión", viewModel::logout)
            }
        }
    }
}
