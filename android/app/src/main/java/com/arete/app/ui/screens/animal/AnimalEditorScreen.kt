package com.arete.app.ui.screens.animal

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arete.app.AreteApplication
import com.arete.app.data.model.*
import com.arete.app.ui.components.*
import com.arete.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AnimalEditorScreen(onBack: () -> Unit, onSaved: (String) -> Unit,
    viewModel: AnimalEditorViewModel = viewModel(factory = AnimalEditorViewModel.Factory)) {
    val state by viewModel.state.collectAsState()
    val app = LocalContext.current.applicationContext as AreteApplication
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importPhoto) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { viewModel.importPhoto(Uri.parse(it)) }
        cameraUri = null
    }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    AnimalEditorContent(state, viewModel::change, viewModel::sex, viewModel::step, viewModel::save, viewModel::load, onBack,
        onChoosePhoto = { runCatching { picker.launch(arrayOf("image/*")) }.onFailure { viewModel.photoError() } },
        onCamera = { runCatching {
            val uri = app.container.animalPhotoStore.cameraUri()
            cameraUri = uri.toString()
            camera.launch(uri)
        }.onFailure { viewModel.photoError() } })
}

@Composable
internal fun AnimalEditorContent(
    state: AnimalEditorState,
    onChange: (AnimalField, (AnimalForm) -> AnimalForm) -> Unit,
    onSex: (AnimalSex) -> Unit, onStep: (Int) -> Unit, onSave: () -> Unit,
    onRetry: () -> Unit, onBack: () -> Unit, onChoosePhoto: () -> Unit = {}, onCamera: () -> Unit = {}
) {
    val context = state.context
    val form = state.form
    val busy = state.saving || state.photoBusy
    val editable = context?.canEdit == true && !busy
    val locked = context?.existing?.birthLocked == true
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val finishTyping: () -> Unit = { focus.requestFocus(); keyboard?.hide() }
    var discard by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = { if (!busy) { if (state.dirty) discard = true else onBack() } }
    BackHandler { back() }
    LaunchedEffect(state.step, state.validationAttempt) { scroll.scrollTo(0) }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, containerColor = Paper,
        title = { Text("¿Salir sin guardar?") }, text = { Text("Los cambios de esta ficha se descartarán.") },
        confirmButton = { TextButton(onClick = { discard = false; onBack() }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = { discard = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Seguir editando") } })

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).focusRequester(focus).focusable()
                .testTag("animal-form-scroll").padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.fillMaxWidth().background(if (form.sex == AnimalSex.FEMALE) FemaleBackground else MaleBackground,
                    MaterialTheme.shapes.small).padding(16.dp)) {
                    Text(if (context?.existing == null) "Agregar animal" else "Editar ficha", style = MaterialTheme.typography.headlineLarge)
                    Text(if (form.sex == AnimalSex.FEMALE) "♀ Hembra" else "♂ Macho", style = MaterialTheme.typography.labelLarge)
                }
                if (context != null) {
                    Text(context.ranchName, style = MaterialTheme.typography.titleMedium)
                    if (context.isDemo) Text("Datos de ejemplo · guardado solo en este dispositivo", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                }
                when {
                    state.loading -> { CircularProgressIndicator(); Text("Cargando ficha…") }
                    context == null -> {
                        Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        AreteButton("Reintentar", onRetry)
                    }
                    !context.canEdit -> Text(state.error ?: "No tienes permiso para agregar o editar animales.",
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("animal-permission-denied"))
                    else -> {
                        Text("Paso ${state.step + 1} de 3 · ${listOf("Identificación", "Origen y padres", "Foto y datos adicionales")[state.step]}",
                            style = MaterialTheme.typography.titleLarge)
                        Text("* Campo obligatorio", style = MaterialTheme.typography.bodyMedium)
                        if (locked) Text("Ficha vinculada a un parto: sexo, nacimiento, madre y tipo de parto están bloqueados.", style = MaterialTheme.typography.bodyMedium)
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("animal-general-error")) }
                        when (state.step) {
                            0 -> {
                                FormInput("Folio *", AnimalField.FOLIO, form.folio, state, editable, finishTyping) { value -> onChange(AnimalField.FOLIO) { it.copy(folio = value) } }
                                FormInput("SINIIGA (opcional)", AnimalField.SINIIGA, form.siniiga, state, editable, finishTyping, KeyboardType.Number) { value -> onChange(AnimalField.SINIIGA) { it.copy(siniiga = value) } }
                                ChoiceField("Raza *", AnimalField.BREED, form.breed, context.breeds, state, editable, finishTyping) { value -> onChange(AnimalField.BREED) { it.copy(breed = value) } }
                                FormInput("Color *", AnimalField.COLOR, form.color, state, editable, finishTyping) { value -> onChange(AnimalField.COLOR) { it.copy(color = value) } }
                                Text("Sexo *", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AnimalSex.entries.forEach { sex ->
                                        val female = sex == AnimalSex.FEMALE
                                        OutlinedButton(onClick = { finishTyping(); onSex(sex) }, enabled = editable && !locked,
                                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("animal-sex-${sex.name}"),
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (form.sex == sex) { if (female) FemaleBackground else MaleBackground } else Paper,
                                                contentColor = if (female) FemaleText else MaleText)) {
                                            Text((if (form.sex == sex) "✓ " else "") + if (female) "♀ Hembra" else "♂ Macho", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                                FieldError(state, AnimalField.SEX)
                                val categories = AnimalCategory.entries.filter {
                                    when (it) { AnimalCategory.EWE, AnimalCategory.REPLACEMENT -> form.sex == AnimalSex.FEMALE
                                        AnimalCategory.STUD -> form.sex == AnimalSex.MALE; else -> true }
                                }
                                ChoiceField("Categoría *", AnimalField.CATEGORY, form.category.label, categories.map { it.label }, state, editable, finishTyping) { value ->
                                    onChange(AnimalField.CATEGORY) { it.copy(category = categories.first { category -> category.label == value }) }
                                }
                            }
                            1 -> {
                                ChoiceField("Motivo de ingreso *", AnimalField.REASON, form.reason?.label.orEmpty(), EntryReason.entries.map { it.label }, state, editable, finishTyping) { value ->
                                    onChange(AnimalField.REASON) { it.copy(reason = EntryReason.entries.first { reason -> reason.label == value }) }
                                }
                                FormInput("Nacimiento * (dd/mm/aaaa)", AnimalField.BIRTH_DATE, form.birthDate, state, editable && !locked, finishTyping, KeyboardType.Text) { value -> onChange(AnimalField.BIRTH_DATE) { it.copy(birthDate = value) } }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(form.approximateDate, { value -> onChange(AnimalField.BIRTH_DATE) { it.copy(approximateDate = value) } }, enabled = editable && !locked)
                                    Text("Fecha aproximada", style = MaterialTheme.typography.bodyLarge)
                                }
                                ParentField("Madre", AnimalSex.FEMALE, AnimalField.MOTHER, form.mother, state, editable && !locked, finishTyping) { choice -> onChange(AnimalField.MOTHER) { it.copy(mother = choice) } }
                                ParentField("Padre", AnimalSex.MALE, AnimalField.FATHER, form.father, state, editable, finishTyping) { choice -> onChange(AnimalField.FATHER) { it.copy(father = choice) } }
                                ChoiceField("Tipo de parto", AnimalField.BIRTH_TYPE, form.birthType, listOf("", "Simple", "Gemelar", "Triple", "Cuádruple", "Quíntuple"), state, editable && !locked, finishTyping) { value -> onChange(AnimalField.BIRTH_TYPE) { it.copy(birthType = value) } }
                                ChoiceField("Concepción", AnimalField.CONCEPTION, form.conception, listOf("", "Monta natural (MN)", "Inseminación artificial (IA)", "Transferencia embrionaria (TE)"), state, editable, finishTyping) { value -> onChange(AnimalField.CONCEPTION) { it.copy(conception = value) } }
                            }
                            2 -> {
                                if (form.sex == AnimalSex.FEMALE) ChoiceField("Estado reproductivo", AnimalField.REPRODUCTIVE_STATE, form.reproductiveState,
                                    listOf("", "Vacía", "En empadre", "Gestante", "Lactancia"), state, editable, finishTyping) { value -> onChange(AnimalField.REPRODUCTIVE_STATE) { it.copy(reproductiveState = value) } }
                                else ChoiceField("Destino", AnimalField.DESTINATION, form.destination,
                                    listOf("", "Pie de cría", "Engorda", "Rastro", "Reemplazo", "Venta"), state, editable, finishTyping) { value -> onChange(AnimalField.DESTINATION) { it.copy(destination = value) } }
                                FormInput("Registro UNO", AnimalField.REGISTRATION, form.registration, state, editable, finishTyping) { value -> onChange(AnimalField.REGISTRATION) { it.copy(registration = value) } }
                                FormInput("ADN", AnimalField.DNA, form.dna, state, editable, finishTyping) { value -> onChange(AnimalField.DNA) { it.copy(dna = value) } }
                                FormInput("Criador", AnimalField.BREEDER, form.breeder, state, editable, finishTyping) { value -> onChange(AnimalField.BREEDER) { it.copy(breeder = value) } }
                                FormInput("Propietario actual", AnimalField.OWNER, form.owner, state, editable, finishTyping) { value -> onChange(AnimalField.OWNER) { it.copy(owner = value) } }
                                Text("Foto (opcional)", style = MaterialTheme.typography.titleMedium)
                                form.photoPath?.let { path ->
                                    val photo by produceState<android.graphics.Bitmap?>(null, path) {
                                        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
                                    }
                                    photo?.let { Image(it.asImageBitmap(), "Foto del animal", Modifier.fillMaxWidth().height(180.dp).testTag("animal-photo")) }
                                    AreteSecondaryButton("Quitar foto", { onChange(AnimalField.PHOTO) { it.copy(photoPath = null) } }, enabled = editable)
                                }
                                AreteSecondaryButton(if (state.photoBusy) "Preparando foto…" else "Elegir foto", { finishTyping(); onChoosePhoto() }, enabled = editable)
                                AreteSecondaryButton("Tomar foto", { finishTyping(); onCamera() }, enabled = editable)
                                FieldError(state, AnimalField.PHOTO)
                                FormInput("Notas", AnimalField.NOTES, form.notes, state, editable, finishTyping, singleLine = false) { value -> onChange(AnimalField.NOTES) { it.copy(notes = value) } }
                                context.existing?.let { Text("Situación: ${it.situation.label}. Las bajas se registran desde su flujo correspondiente.", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(Paper).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context?.canEdit == true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.step > 0) AreteSecondaryButton("Anterior", { finishTyping(); onStep(state.step - 1) }, Modifier.weight(1f).testTag("animal-previous"), enabled = !busy)
                        if (state.step < 2) AreteButton("Siguiente", { finishTyping(); onStep(state.step + 1) }, Modifier.weight(1f).testTag("animal-next"), enabled = !busy)
                        else AreteButton(if (state.saving) "Guardando…" else "Guardar ficha", { finishTyping(); onSave() }, Modifier.weight(1f).testTag("animal-save"), enabled = !busy)
                    }
                }
                TextButton(onClick = back, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Volver al inventario", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun FormInput(label: String, field: AnimalField, value: String, state: AnimalEditorState, enabled: Boolean,
    finishTyping: () -> Unit, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value, onValue, modifier = Modifier.fillMaxWidth().testTag("animal-field-${field.name}"), enabled = enabled,
            isError = state.errors.containsKey(field), singleLine = singleLine, textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { finishTyping() }))
        FieldError(state, field)
    }
}

@Composable
private fun ChoiceField(label: String, field: AnimalField, value: String, choices: List<String>, state: AnimalEditorState,
    enabled: Boolean, finishTyping: () -> Unit, onValue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        AreteSecondaryButton(value.ifBlank { "Seleccionar" }, { finishTyping(); expanded = true }, Modifier.testTag("animal-field-${field.name}"), enabled)
        FieldError(state, field)
    }
    if (expanded) SelectionDialog(label, choices.map { it to it.ifBlank { "Sin especificar" } }, { expanded = false }) {
        onValue(it); expanded = false
    }
}

@Composable
private fun ParentField(label: String, sex: AnimalSex, field: AnimalField, choice: ParentChoice, state: AnimalEditorState,
    enabled: Boolean, finishTyping: () -> Unit, onChoice: (ParentChoice) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val parents = parentCandidates(state.context!!, sex)
    val unknown = if (sex == AnimalSex.FEMALE) "Desconocida" else "Desconocido"
    val external = if (sex == AnimalSex.FEMALE) "Externa" else "Externo"
    val value = when (choice.kind) { ParentKind.UNKNOWN -> unknown; ParentKind.EXTERNAL -> external
        ParentKind.LOCAL -> parents.find { it.id == choice.id }?.form?.folio ?: "Progenitor no disponible" }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        AreteSecondaryButton(value, { finishTyping(); expanded = true }, Modifier.testTag("animal-field-${field.name}"), enabled)
        if (choice.kind == ParentKind.EXTERNAL) OutlinedTextField(choice.reference, { onChoice(choice.copy(reference = it)) },
            label = { Text("Referencia externa *") }, enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("animal-external-${field.name}"), textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { finishTyping() }))
        FieldError(state, field)
    }
    if (expanded) SelectionDialog("Elegir ${label.lowercase()}", listOf("unknown" to unknown, "external" to external) + parents.map {
        it.id to "${it.form.folio} · ${if (sex == AnimalSex.FEMALE) "♀ Hembra" else "♂ Macho"} · ${it.situation.label}"
    }, { expanded = false }, searchable = true) { id ->
        onChoice(when (id) { "unknown" -> ParentChoice(); "external" -> ParentChoice(ParentKind.EXTERNAL)
            else -> ParentChoice(ParentKind.LOCAL, id) })
        expanded = false
    }
}

@Composable
private fun SelectionDialog(title: String, choices: List<Pair<String, String>>, onDismiss: () -> Unit,
    searchable: Boolean = false, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Paper, title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (searchable) OutlinedTextField(query, { query = it }, label = { Text("Buscar folio") }, singleLine = true, modifier = Modifier.testTag("parent-search"))
                val filtered = choices.filter { it.first in listOf("unknown", "external") || it.second.contains(query.trim(), true) }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(filtered, key = { it.first }) { (id, label) ->
                        TextButton(onClick = { onSelect(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("animal-option-$id")) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (searchable && filtered.size <= 2 && query.isNotBlank()) item { Text("No encontramos ese folio.") }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cerrar") } })
}

@Composable
private fun FieldError(state: AnimalEditorState, field: AnimalField) {
    state.errors[field]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("animal-error-${field.name}")) }
}
