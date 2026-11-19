package com.arete.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arete.app.ui.components.AreteSecondaryButton
import com.arete.app.ui.components.AreteButton
import com.arete.app.ui.components.Sex
import com.arete.app.ui.components.SexChip

@Composable
fun HomeScreen(
    onInventory: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val pendingCount by viewModel.pendingCount.collectAsState()

    val syncStatus = when (pendingCount) {
        0 -> "Al día"
        1 -> "1 registro pendiente por subir"
        else -> "$pendingCount registros pendientes por subir"
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Arete", style = MaterialTheme.typography.headlineLarge)
            Text(text = syncStatus, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SexChip(sex = Sex.FEMALE)
                SexChip(sex = Sex.MALE)
            }
            Spacer(modifier = Modifier.weight(1f))
            AreteButton(text = "Ver inventario", onClick = onInventory)
            AreteSecondaryButton(text = "Cerrar sesión", onClick = viewModel::logout)
        }
    }
}
