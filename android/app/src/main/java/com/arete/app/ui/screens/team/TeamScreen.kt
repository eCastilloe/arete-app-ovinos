package com.arete.app.ui.screens.team

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arete.app.data.model.*
import com.arete.app.ui.components.*
import com.arete.app.ui.theme.*

@Composable
fun TeamScreen(onBack: () -> Unit, model: TeamViewModel = viewModel(factory = TeamViewModel.Factory)) {
    val state by model.state.collectAsState()
    TeamContent(state, model::add, model::edit, model::change, model::password, model::save, model::cancel, model::load, onBack)
}

@Composable
internal fun TeamContent(state: TeamUiState, onAdd: () -> Unit, onEdit: (TeamMember) -> Unit,
    onChange: ((TeamDraft) -> TeamDraft) -> Unit, onPassword: (String) -> Unit, onSave: () -> Unit,
    onCancel: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val finishTyping: () -> Unit = { focus.requestFocus(); keyboard?.hide() }
    val draft = state.draft
    var discard by remember { mutableStateOf(false) }
    var showPassword by remember(draft?.memberId, draft == null) { mutableStateOf(false) }
    val back: () -> Unit = { if (!state.saving) {
        if (draft != null && state.dirty) discard = true
        else if (draft != null) onCancel() else onBack()
    } }
    BackHandler(enabled = draft != null || state.saving) { back() }
    LaunchedEffect(draft?.memberId, draft == null, state.validationAttempt) { scroll.scrollTo(0) }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text("¿Salir sin guardar?") }, text = { Text("Se descartarán los cambios de este encargado.") },
        confirmButton = { TextButton(onClick = { discard = false; onCancel() }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = { discard = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Seguir editando") } })

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scroll).focusRequester(focus).focusable()
                    .testTag("team-scroll").padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Mi equipo", style = MaterialTheme.typography.headlineLarge)
                    state.snapshot?.let {
                        Text(it.ranchName, style = MaterialTheme.typography.titleMedium)
                        if (it.isDemo) Text("Modo de prueba · las cuentas solo se guardan aquí y no sirven para iniciar sesión.", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.error?.let { Text(it, color = AlertText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("team-error")) }
                    state.notice?.let { Text(it, color = Agave, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("team-notice")) }
                    when {
                        state.loading -> { CircularProgressIndicator(); Text("Cargando equipo…") }
                        state.snapshot == null -> AreteButton("Intentar de nuevo", onRetry, Modifier.testTag("team-retry"))
                        draft == null -> {
                            AreteButton("Agregar encargado", onAdd, Modifier.testTag("team-add"))
                            if (state.snapshot.members.isEmpty()) Text("Todavía no hay encargados en tu equipo.", style = MaterialTheme.typography.bodyLarge)
                            state.snapshot.members.forEach { member ->
                                Column(Modifier.fillMaxWidth().testTag("team-member-${member.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(member.name, style = MaterialTheme.typography.titleMedium)
                                    Text(member.email, style = MaterialTheme.typography.bodyLarge)
                                    Text(permissionSummary(member.permissions), style = MaterialTheme.typography.bodyMedium)
                                    AreteSecondaryButton("Cambiar permisos · ${member.name}", { onEdit(member) }, Modifier.testTag("team-edit-${member.id}"))
                                    HorizontalDivider(color = Line)
                                }
                            }
                        }
                        else -> {
                            val creating = draft.memberId == null
                            Text(if (creating) "Agregar encargado" else "Cambiar permisos", style = MaterialTheme.typography.titleLarge)
                            Text("${if (creating) "Nombre, usuario y contraseña son obligatorios." else "Cambiarás los permisos de ${draft.name}."}", style = MaterialTheme.typography.bodyMedium)
                            TeamInput("Nombre", TeamField.NAME, draft.name, state, creating, finishTyping) { value -> onChange { it.copy(name = value) } }
                            TeamInput("Usuario (correo)", TeamField.EMAIL, draft.email, state, creating, finishTyping, KeyboardType.Email) { value -> onChange { it.copy(email = value) } }
                            if (creating) {
                                TeamInput("Contraseña inicial", TeamField.PASSWORD, state.password, state, true, finishTyping,
                                    KeyboardType.Password, if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), onPassword)
                                AreteSecondaryButton(if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                                    { showPassword = !showPassword }, enabled = !state.saving)
                            }
                            Text("Permisos", style = MaterialTheme.typography.titleLarge)
                            PermissionCheck("Lectura", "Consultar inventario y fichas", "read", draft.permissions.read, !state.saving) { checked -> onChange { it.copy(permissions = it.permissions.copy(read = checked)) } }
                            PermissionCheck("Captura", "Registrar partos, pesajes y eventos", "capture", draft.permissions.capture, !state.saving) { checked -> onChange { it.copy(permissions = it.permissions.copy(capture = checked)) } }
                            PermissionCheck("Edición", "Agregar y editar animales; registrar bajas", "edit", draft.permissions.edit, !state.saving) { checked -> onChange { it.copy(permissions = it.permissions.copy(edit = checked)) } }
                            PermissionCheck("Exportación", "Generar y descargar la Sábana", "export", draft.permissions.export, !state.saving) { checked -> onChange { it.copy(permissions = it.permissions.copy(export = checked)) } }
                            if (permissionSummary(draft.permissions) == "Sin permisos") Text("Sin permisos, el encargado no podrá consultar ni registrar datos del rancho.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (draft != null && state.snapshot != null) AreteButton(
                    if (state.saving) "Guardando…" else if (draft.memberId == null) "Guardar encargado" else "Guardar permisos",
                    { finishTyping(); onSave() }, Modifier.testTag("team-save"), enabled = !state.saving && !state.loading)
                AreteSecondaryButton(if (draft == null) "Volver a Inicio" else "Volver a Mi equipo", back,
                    Modifier.testTag("team-back"), enabled = !state.saving)
            }
        }
    }
}

@Composable
private fun TeamInput(label: String, field: TeamField, value: String, state: TeamUiState, editable: Boolean,
    onDone: () -> Unit, keyboardType: KeyboardType = KeyboardType.Text,
    transformation: VisualTransformation = VisualTransformation.None, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth().testTag("team-field-${field.name}"),
            enabled = editable && !state.saving, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = transformation, isError = field in state.fields,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }))
        state.fields[field]?.let { Text(it, color = AlertText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("team-field-error-${field.name}")) }
    }
}

@Composable
private fun PermissionCheck(label: String, description: String, tag: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("team-permission-$tag")
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun permissionSummary(value: RanchPermissions): String = buildList {
    if (value.read) add("Lectura")
    if (value.capture) add("Captura")
    if (value.edit) add("Edición")
    if (value.export) add("Exportación")
}.joinToString(" · ").ifBlank { "Sin permisos" }
