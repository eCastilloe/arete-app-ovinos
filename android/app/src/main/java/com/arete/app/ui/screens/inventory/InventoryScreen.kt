package com.arete.app.ui.screens.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arete.app.data.model.*
import com.arete.app.ui.components.AreteButton
import com.arete.app.ui.components.AreteSecondaryButton
import com.arete.app.ui.theme.*
import java.util.Locale

@Composable
fun InventoryScreen(onHome: () -> Unit, viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory),
    onAdd: (() -> Unit)? = null, onEdit: ((String) -> Unit)? = null, refreshKey: Long = 0L) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(refreshKey) { if (refreshKey != 0L) viewModel.retry() }
    InventoryContent(state, viewModel::search, viewModel::selectCategory, viewModel::toggleDischarged, viewModel::retry, onHome, onAdd, onEdit)
}

@Composable
internal fun InventoryContent(
    state: InventoryUiState,
    onQuery: (String) -> Unit,
    onCategory: (InventoryCategory) -> Unit,
    onDischarged: () -> Unit,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    onAdd: (() -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val contentFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Give focus a non-editable destination; clearing it alone can refocus the input.
    val finishTyping: () -> Unit = { contentFocus.requestFocus(); keyboard?.hide() }
    LaunchedEffect(state.filters.category, state.filters.query, state.filters.showDischarged) {
        listState.scrollToItem(0)
    }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Agave, selectedTextColor = Agave,
                    indicatorColor = Agave.copy(alpha = .12f),
                    unselectedIconColor = InkSecondary, unselectedTextColor = InkSecondary
                )
                NavigationBarItem(selected = false, onClick = onHome,
                    colors = colors,
                    icon = { Text("⌂", style = MaterialTheme.typography.titleLarge) },
                    label = { Text("Inicio", style = MaterialTheme.typography.labelMedium) })
                NavigationBarItem(selected = true, onClick = {},
                    colors = colors,
                    icon = { Text("≡", style = MaterialTheme.typography.titleLarge) },
                    label = { Text("Inventario", style = MaterialTheme.typography.labelMedium) })
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .focusRequester(contentFocus).focusable().testTag("inventory-list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header", contentType = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Inventario", style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() })
                    if (!state.isLoading && state.error == null) {
                        Text(state.ranchName, style = MaterialTheme.typography.titleMedium)
                        Text(if (state.isDemo) "Datos de ejemplo · prueba local" else "Inventario local",
                            style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                    }
                }
            }
            when {
                state.isLoading -> item(key = "loading") {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text("Cargando inventario…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                state.error != null -> item(key = "error") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        AreteButton("Reintentar", onRetry)
                    }
                }
                else -> {
                    if (state.canEdit && onAdd != null) item(key = "add-animal", contentType = "controls") {
                        AreteButton("Agregar animal", { finishTyping(); onAdd() }, Modifier.testTag("add-animal"))
                    }
                    item(key = "search", contentType = "controls") {
                        OutlinedTextField(
                            value = state.filters.query, onValueChange = onQuery,
                            label = { Text("Buscar por folio o SINIIGA", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth().testTag("inventory-search"),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { finishTyping() }),
                            trailingIcon = if (state.filters.query.isNotEmpty()) ({
                                TextButton(onClick = { onQuery(""); finishTyping() }, modifier = Modifier.heightIn(min = 56.dp)) {
                                    Text("Limpiar", style = MaterialTheme.typography.labelMedium)
                                }
                            }) else null
                        )
                    }
                    item(key = "categories", contentType = "controls") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InventoryCategory.entries.forEach { category ->
                                val selected = !state.filters.showDischarged && state.filters.query.isBlank() && state.filters.category == category
                                Column(
                                    modifier = Modifier.weight(1f).heightIn(min = 72.dp)
                                        .background(if (selected) Agave else Color.Transparent, MaterialTheme.shapes.small)
                                        .border(1.dp, if (selected) Agave else Line, MaterialTheme.shapes.small)
                                        .selectable(selected, role = Role.Tab, onClick = { finishTyping(); onCategory(category) })
                                        .testTag("category-${category.name}").padding(horizontal = 4.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(category.label, color = if (selected) White else Ink,
                                        style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                    Text((state.counts[category] ?: 0).toString(), color = if (selected) White else Ink,
                                        style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                        }
                    }
                    item(key = "discharged", contentType = "controls") {
                        AreteSecondaryButton(
                            text = if (state.filters.showDischarged) "Volver a activos" else "Ver bajas (${state.dischargedCount})",
                            onClick = { finishTyping(); onDischarged() }, modifier = Modifier.testTag("show-discharged")
                        )
                    }
                    item(key = "summary", contentType = "summary") {
                        val title = when {
                            state.filters.showDischarged -> "Bajas"
                            state.filters.query.isNotBlank() -> "Resultados en todos los apartados"
                            else -> state.filters.category.label
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("$title · ${state.animals.size}", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag("inventory-result-count"))
                            if (state.filters.query.isBlank() && !state.filters.showDischarged) {
                                Text(state.averageWeightKg?.let { "Peso promedio: ${formatKg(it)} kg" } ?: "Peso promedio: sin pesajes",
                                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("average-weight"))
                                if (state.weighedCount > 0) Text("Último pesaje de ${state.weighedCount} de ${state.animals.size} animales",
                                    style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                            }
                        }
                    }
                    if (state.animals.isEmpty()) item(key = "empty") {
                        Text(when {
                            state.filters.query.isNotBlank() -> "No encontramos ese folio o SINIIGA. Revisa lo escrito o limpia la búsqueda."
                            state.filters.showDischarged -> "No hay animales dados de baja."
                            else -> "No hay animales activos en este apartado."
                        }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("inventory-empty"))
                    }
                    items(state.animals, key = { it.id }, contentType = { "animal" }) { animal ->
                        AnimalRow(animal, if (state.canEdit) onEdit else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimalRow(animal: InventoryAnimal, onEdit: ((String) -> Unit)?) {
    Column(Modifier.fillMaxWidth().testTag("animal-${animal.id}"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimalTag(animal, Modifier.width(118.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(animal.breed, style = MaterialTheme.typography.titleMedium)
                Text(if (animal.situation != AnimalSituation.ACTIVE) animal.situation.label
                    else animal.reproductiveState ?: animal.category.label, style = MaterialTheme.typography.bodyMedium)
                Text(animal.lastWeightKg?.takeIf { it.isFinite() && it > 0 }?.let { "${formatKg(it)} kg" } ?: "Sin pesaje",
                    style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
            }
        }
        animal.siniiga?.let { number ->
            Text("SINIIGA $number", style = MaterialTheme.typography.bodySmall, color = Ink,
                modifier = Modifier.background(Siniiga, MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (onEdit != null) AreteSecondaryButton("Editar ficha · ${animal.folio}", { onEdit(animal.id) }, Modifier.testTag("edit-${animal.id}"))
        HorizontalDivider(color = Line)
    }
}

private val TagShape = GenericShape { size, _ ->
    moveTo(size.width * .22f, 0f); lineTo(size.width * .78f, 0f)
    lineTo(size.width, size.width * .20f); lineTo(size.width, size.height)
    lineTo(0f, size.height); lineTo(0f, size.width * .20f); close()
}

@Composable
private fun AnimalTag(animal: InventoryAnimal, modifier: Modifier = Modifier) {
    val female = animal.sex == AnimalSex.FEMALE
    val border = if (female) FemaleBorder else MaleBorder
    val ink = if (female) FemaleText else MaleText
    Column(modifier.background(if (female) FemaleBackground else MaleBackground, TagShape)
        .border(1.dp, border, TagShape).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(border, style = Stroke(width = 1.dp.toPx())) }
        Text(animal.folio, color = ink, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(if (female) "♀ Hembra" else "♂ Macho", color = ink, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatKg(value: Double): String = String.format(Locale.forLanguageTag("es-MX"), "%.1f", value)
