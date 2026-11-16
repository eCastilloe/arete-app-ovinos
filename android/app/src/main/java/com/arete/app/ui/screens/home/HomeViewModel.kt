package com.arete.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arete.app.AreteApplication
import com.arete.app.data.repository.AuthRepository
import com.arete.app.data.repository.SyncRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authRepository: AuthRepository,
    syncRepository: SyncRepository
) : ViewModel() {

    val pendingCount: StateFlow<Int> = syncRepository.pendingCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AreteApplication
                HomeViewModel(app.container.authRepository, app.container.syncRepository)
            }
        }
    }
}