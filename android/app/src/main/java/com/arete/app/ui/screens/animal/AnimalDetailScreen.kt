package com.arete.app.ui.screens.animal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arete.app.data.model.*
import com.arete.app.ui.components.AreteButton
import com.arete.app.ui.components.AreteSecondaryButton
import com.arete.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun AnimalDetailScreen(onBack: () -> Unit, onHome: () -> Unit, onEdit: (String) -> Unit,
    refreshKey: Long = 0L, viewModel: AnimalDetailViewModel = viewModel(factory = AnimalDetailViewModel.Factory)) {
    val state by viewModel.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.reload() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refreshKey) { if (refreshKey != 0L) viewModel.reload() }
    AnimalDetailContent(state, viewModel::reload, onBack, onHome, onEdit)
}

@Composable
internal fun AnimalDetailContent(state: AnimalDetailState, onRetry: () -> Unit, onBack: () -> Unit,
    onHome: () -> Unit, onEdit: (String) -> Unit) {
    val detail = state.detail
    val scroll = rememberScrollState()
    Scaffold(bottomBar = {
        Column(Modifier.fillMaxWidth().background(Paper).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            AreteSecondaryButton("Volver al inventario", onBack, Modifier.testTag("detail-back"))
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scroll).testTag("detail-scroll").padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Ficha del animal", style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() })
                when {
                    state.loading -> {
                        CircularProgressIndicator()
                        Text("Cargando ficha…", style = MaterialTheme.typography.bodyLarge)
                    }
                    state.error != null -> {
                        Text(state.error, color = AlertText, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("detail-error"))
                        AreteButton("Intentar de nuevo", onRetry, Modifier.testTag("detail-retry"))
                    }
                    detail != null -> {
                        val animal = detail.animal
                        val form = animal.form
                        val female = form.sex == AnimalSex.FEMALE
                        val ink = if (female) FemaleText else MaleText
                        Text(detail.ranchName, style = MaterialTheme.typography.titleMedium)
                        if (detail.isDemo) Text("Modo de prueba · datos de ejemplo", style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.fillMaxWidth().background(if (female) FemaleBackground else MaleBackground,
                            MaterialTheme.shapes.medium).testTag(if (female) "detail-female" else "detail-male").padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Arete ${form.folio}", color = ink, style = MaterialTheme.typography.displayMedium)
                            Text(if (female) "♀ Hembra" else "♂ Macho", color = ink, style = MaterialTheme.typography.titleMedium)
                            Text("${form.breed} · ${form.color}", color = ink, style = MaterialTheme.typography.bodyLarge)
                            val age = animalAge(parseBirthDate(form.birthDate), state.onDate)?.label ?: "Edad no disponible"
                            Text(if (form.approximateDate && age != "Edad no disponible") "$age (aproximada)" else age,
                                color = ink, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("detail-age"))
                            Text("Nacimiento: ${form.birthDate.ifBlank { "Sin registrar" }}", color = ink,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        if (form.siniiga.isNotBlank()) Text("SINIIGA ${form.siniiga}", color = Ink,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().background(Siniiga).padding(12.dp))
                        DetailValue("Categoría", form.category.label)
                        DetailValue("Situación", animal.situation.label)
                        if (female) DetailValue("Estado reproductivo", form.reproductiveState)
                        else DetailValue("Destino", form.destination)
                        DetailValue("Último peso", animal.lastWeightKg?.takeIf { it.isFinite() && it > 0 }
                            ?.let { "${decimal(it)} kg" } ?: "Sin pesaje")
                        if (!female) DetailValue("Circunferencia testicular", animal.lastTesticularCircumferenceCm
                            ?.takeIf { it.isFinite() && it > 0 && it < 60 }?.let { "${decimal(it)} cm" } ?: "Sin registrar",
                            Modifier.testTag("detail-circumference"))
                        HorizontalDivider(color = Line)
                        Text("Foto", style = MaterialTheme.typography.titleLarge)
                        DetailPhoto(form.photoPath)
                        HorizontalDivider(color = Line)
                        Text("Origen y registro", style = MaterialTheme.typography.titleLarge)
                        DetailValue("Motivo de ingreso", form.reason?.label.orEmpty())
                        DetailValue("Madre", detail.motherLabel)
                        DetailValue("Semental", detail.fatherLabel)
                        DetailValue("Tipo de parto", form.birthType)
                        DetailValue("Concepción", form.conception)
                        DetailValue("Registro UNO", form.registration)
                        DetailValue("ADN", form.dna)
                        DetailValue("Criador", form.breeder)
                        DetailValue("Propietario actual", form.owner)
                        if (form.notes.isNotBlank()) DetailValue("Notas", form.notes)
                        if (detail.canEdit) AreteButton("Editar ficha", { onEdit(animal.id) }, Modifier.testTag("detail-edit"))
                    }
                }
                AreteSecondaryButton("Ir a Inicio", onHome, Modifier.testTag("detail-home"))
            }
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value.ifBlank { "Sin registrar" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DetailPhoto(path: String?) {
    val photo by produceState<Bitmap?>(null, path) {
        value = null
        value = withContext(Dispatchers.IO) {
            path?.let { runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(it, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
                BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull() }
        }
    }
    val bitmap = photo
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Foto del animal",
        Modifier.fillMaxWidth().height(220.dp).testTag("detail-photo"))
    else Text(if (path == null) "Sin foto" else "Foto no disponible", style = MaterialTheme.typography.bodyLarge)
}

private fun decimal(number: Double) = String.format(Locale.forLanguageTag("es-MX"), "%.1f", number)
