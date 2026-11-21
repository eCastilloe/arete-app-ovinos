package com.arete.app.ui.screens.login

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arete.app.ui.theme.AreteTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typingShowingPasswordAndSubmittingWorkWithKeyboard() {
        var submitted: LoginUiState? = null
        compose.setContent {
            var state by remember { mutableStateOf(LoginUiState()) }
            AreteTheme {
                LoginContent(state, { state = state.copy(email = it) },
                    { state = state.copy(password = it) }, { submitted = state }, {}, showDemo = false)
            }
        }
        compose.onNodeWithText("Usuario (correo)").performTextInput("prueba@example.test")
        compose.onNodeWithText("Contraseña", substring = false).performTextInput("Prueba123!")
        compose.onNodeWithText("Mostrar contraseña").performScrollTo().performClick()
        compose.onNodeWithText("Prueba123!").assertExists()
        compose.onNodeWithText("Entrar", substring = false).performScrollTo()
            .assertHeightIsAtLeast(56.dp).performClick()
        compose.runOnIdle {
            assertEquals("prueba@example.test", submitted?.email)
            assertEquals("Prueba123!", submitted?.password)
        }
    }

    @Test fun errorKeepsUsernameVisibleAndAllowsRetry() {
        compose.setContent {
            var state by remember { mutableStateOf(LoginUiState(email = "prueba@example.test", password = "incorrecta")) }
            AreteTheme {
                LoginContent(state, {}, {}, {
                    state = state.copy(error = "Usuario o contraseña incorrectos")
                }, {}, showDemo = false)
            }
        }
        compose.onNodeWithText("Entrar", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Usuario o contraseña incorrectos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("prueba@example.test").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Entrar", substring = false).assertIsEnabled()
    }

    @Test fun loadingDisablesFormAndDemoCanBeExcluded() {
        compose.setContent {
            AreteTheme { LoginContent(LoginUiState(isLoading = true), {}, {}, {}, {}, showDemo = false) }
        }
        compose.onNodeWithText("Usuario (correo)").assertIsNotEnabled()
        compose.onNodeWithText("Contraseña", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Entrando…").assertIsNotEnabled()
        compose.onNodeWithText("Entrar en modo demo").assertDoesNotExist()
    }
}
