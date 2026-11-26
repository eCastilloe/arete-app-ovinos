package com.arete.app.ui.screens.login

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arete.app.BuildConfig
import com.arete.app.ui.components.AreteButton
import com.arete.app.ui.components.AreteSecondaryButton
import com.arete.app.ui.theme.*

@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)) {
    val state by viewModel.state.collectAsState()
    LoginContent(state, viewModel::changeEmail, viewModel::changePassword, viewModel::login, viewModel::enterDemoMode)
}

@Composable
internal fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onDemo: () -> Unit,
    showDemo: Boolean = BuildConfig.DEBUG
) {
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val submit = { focusManager.clearFocus(); onLogin() }
    Scaffold { innerPadding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val viewportHeight = maxHeight
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).heightIn(min = viewportHeight).padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                    ) {
                        LoginTag("79", "♀ Hembra", FemaleBackground, FemaleBorder, FemaleText)
                        LoginTag("312", "♂ Macho", MaleBackground, MaleBorder, MaleText)
                    }
                    Text("Arete", style = MaterialTheme.typography.displayMedium)
                    Text("Entrar a tu rancho", style = MaterialTheme.typography.headlineMedium)
                    Text("La Asociación te proporciona tu usuario y contraseña.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = state.email, onValueChange = onEmailChange,
                        modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading, singleLine = true,
                        label = { Text("Usuario (correo)") }, placeholder = { Text("Escribe tu correo") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = state.password, onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading, singleLine = true,
                        label = { Text("Contraseña") }, placeholder = { Text("Escribe tu contraseña") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!state.isLoading) submit() })
                    )
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
                            value = showPassword, enabled = !state.isLoading,
                            role = Role.Checkbox, onValueChange = { showPassword = it }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = showPassword, onCheckedChange = null, enabled = !state.isLoading)
                        Spacer(Modifier.width(12.dp))
                        Text("Mostrar contraseña", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Necesitas internet para iniciar sesión. Después puedes volver a abrir la app sin señal mientras tu sesión siga vigente.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.isLoading) Text("Conectando… El primer acceso puede tardar alrededor de un minuto.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    AreteButton(if (state.isLoading) "Entrando…" else "Entrar", submit, enabled = !state.isLoading)
                    if (showDemo) AreteSecondaryButton("Entrar en modo demo", onDemo, enabled = !state.isLoading)
                }
            }
        }
    }
}

@Composable
private fun LoginTag(number: String, label: String, fill: Color, border: Color, ink: Color) {
    Box(Modifier.size(width = 108.dp, height = 130.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 1.dp.toPx()
            val tag = Path().apply {
                moveTo(size.width * .25f, inset)
                lineTo(size.width * .75f, inset)
                lineTo(size.width - inset, size.height * .22f)
                lineTo(size.width - inset, size.height - inset)
                lineTo(inset, size.height - inset)
                lineTo(inset, size.height * .22f)
                close()
            }
            drawPath(tag, fill)
            drawPath(tag, border, style = Stroke(2.dp.toPx()))
            drawCircle(Paper, 5.dp.toPx(), Offset(size.width / 2, 15.dp.toPx()))
            drawCircle(border, 5.dp.toPx(), Offset(size.width / 2, 15.dp.toPx()), style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, color = ink, style = MaterialTheme.typography.displayMedium)
            Text(label, color = ink, style = MaterialTheme.typography.labelMedium)
        }
    }
}
