package com.arete.app.ui.screens.team

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arete.app.data.repository.*
import com.arete.app.ui.components.AreteSecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DemoTeamControls(repository: TeamRepository) {
    val demo = repository as? DemoTeamRepository ?: return
    val access by demo.access.collectAsState()
    val members by demo.members.collectAsState()
    if (access?.isDemo != true) return
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Prueba de permisos", style = MaterialTheme.typography.titleMedium)
        Text("Cambia la vista de ejemplo para comprobar qué puede hacer cada encargado.", style = MaterialTheme.typography.bodyMedium)
        AreteSecondaryButton("Ver como: ${access?.userName}", { open = true }, Modifier.testTag("demo-role"), enabled = !busy)
        error?.let { Text(it) }
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Vista de prueba") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                val choices = listOf(null to "Productor de ejemplo") + members.map { it.id to it.name }
                items(choices.size) { index ->
                    val (id, name) = choices[index]
                    AreteSecondaryButton(name, {
                        open = false; busy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { demo.selectDemoUser(id) }; error = null }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { error = "No pudimos cambiar la vista de prueba." }
                            finally { busy = false }
                        }
                    })
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancelar") } })
}
