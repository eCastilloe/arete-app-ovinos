package com.arete.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arete.app.AreteApplication
import com.arete.app.BuildConfig
import com.arete.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state = mutableState.asStateFlow()

    fun changeEmail(email: String) {
        if (!state.value.isLoading) mutableState.update { it.copy(email = email, error = null) }
    }

    fun changePassword(password: String) {
        if (!state.value.isLoading) mutableState.update { it.copy(password = password, error = null) }
    }

    fun login() {
        val input = state.value
        if (input.isLoading) return
        if (input.email.isBlank() || input.password.isBlank()) {
            mutableState.update { it.copy(error = "Escribe tu correo y contraseña.") }
            return
        }
        mutableState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.login(input.email.trim(), input.password)
            mutableState.update {
                it.copy(
                    isLoading = false,
                    password = if (result.isSuccess) "" else it.password,
                    error = result.exceptionOrNull()?.let(::loginError)
                )
            }
        }
    }

    fun enterDemoMode() {
        if (!BuildConfig.DEBUG || state.value.isLoading) return
        viewModelScope.launch { authRepository.saveDemoSession() }
    }

    private fun loginError(error: Throwable): String = when (error) {
        is HttpException -> when (error.code()) {
            401 -> "Usuario o contraseña incorrectos"
            422 -> "Revisa tu correo y contraseña e inténtalo de nuevo."
            429 -> "Hubo muchos intentos. Espera un momento e inténtalo de nuevo."
            else -> "El servicio no está disponible. Inténtalo de nuevo más tarde."
        }
        is IOException -> "No pudimos conectar. Revisa tu internet e inténtalo de nuevo."
        else -> "No se pudo iniciar sesión. Inténtalo de nuevo."
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AreteApplication
                LoginViewModel(app.container.authRepository)
            }
        }
    }
}
