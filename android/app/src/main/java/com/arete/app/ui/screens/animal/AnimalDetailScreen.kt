package com.arete.app.ui.screens.animal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arete.app.ui.components.AreteSecondaryButton
import com.arete.app.ui.theme.AreteTheme

/**
 * Ficha PROVISIONAL (HU-53). Solo demuestra que la lectura abre la ficha.
 * La ficha real la hace Marvin en HU-19 y reemplaza este archivo,
 * conservando la ruta `animal/{animalId}`.
 */
@Composable
fun AnimalDetailScreen(
    animalId: String,
    onBack: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Ficha del animal", style = MaterialTheme.typography.headlineLarge)
            Text(text = "Identificador: $animalId", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Esta ficha es provisional. La ficha completa llega con la HU-19.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            AreteSecondaryButton(text = "Volver", onClick = onBack)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF0EA)
@Composable
private fun AnimalDetailScreenPreview() {
    AreteTheme {
            AnimalDetailScreen(animalId = "00000000-0000-4000-8000-000000000001", onBack = {})
    }
}