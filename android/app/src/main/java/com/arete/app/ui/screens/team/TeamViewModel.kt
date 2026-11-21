package com.arete.app.ui.screens.team

import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arete.app.AreteApplication
import com.arete.app.data.model.*
import com.arete.app.data.repository.*
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Only non-secret input can survive process recreation.
data class TeamDraft(val name: String = "", val email: String = "", val permissions: RanchPermissions = RanchPermissions(),
    val memberId: String? = null, val expectedVersion: Int? = null)
data class TeamUiState(val loading: Boolean = true, val snapshot: TeamSnapshot? = null,
    val draft: TeamDraft? = null, val password: String = "", val dirty: Boolean = false,
    val saving: Boolean = false, val error: String? = null, val fields: Map<TeamField, String> = emptyMap(),
    val notice: String? = null, val validationAttempt: Int = 0) {
    override fun toString() = "TeamUiState(redacted)"
}

class TeamViewModel(private val repository: TeamRepository, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val gson = Gson()
    private val restored = saved.get<String>("teamDraft")?.let { runCatching { gson.fromJson(it, TeamDraft::class.java) }.getOrNull() }
    private val mutableState = MutableStateFlow(TeamUiState(draft = restored, dirty = restored != null))
    val state = mutableState.asStateFlow()
    init { load() }

    fun load() {
        if (state.value.saving) return
        mutableState.value = state.value.copy(loading = true, snapshot = null, error = null)
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { repository.loadTeam() }
                mutableState.value = state.value.copy(loading = false, snapshot = snapshot)
            } catch (e: CancellationException) { throw e
            } catch (e: RanchAccessDeniedException) { denied()
            } catch (e: Exception) { mutableState.value = state.value.copy(loading = false, error = "No pudimos cargar tu equipo. Intenta de nuevo.") }
        }
    }
    fun add() { if (state.value.snapshot != null && !state.value.saving) setDraft(TeamDraft(), false) }
    fun edit(member: TeamMember) {
        if (state.value.snapshot?.members?.any { it.id == member.id } == true && !state.value.saving)
            setDraft(TeamDraft(member.name, member.email, member.permissions, member.id, member.version), false)
    }
    fun change(transform: (TeamDraft) -> TeamDraft) {
        val draft = state.value.draft ?: return
        if (!state.value.saving) setDraft(transform(draft), true, keepPassword = true)
    }
    fun password(value: String) {
        if (!state.value.saving) mutableState.value = state.value.copy(password = value, dirty = true,
            fields = state.value.fields - TeamField.PASSWORD, error = null)
    }
    private fun setDraft(draft: TeamDraft, dirty: Boolean, keepPassword: Boolean = false) {
        saved["teamDraft"] = gson.toJson(draft)
        mutableState.value = state.value.copy(draft = draft, dirty = dirty,
            password = if (keepPassword) state.value.password else "", fields = emptyMap(), error = null, notice = null)
    }
    fun cancel() {
        if (state.value.saving) return
        saved.remove<String>("teamDraft")
        mutableState.value = state.value.copy(draft = null, password = "", dirty = false, error = null, fields = emptyMap())
    }
    fun save() {
        val current = state.value
        val draft = current.draft ?: return
        if (current.saving || current.snapshot == null) return
        val input = NewTeamMember(draft.name, draft.email, current.password, draft.permissions)
        val fields = if (draft.memberId == null) validateNewMember(input) else emptyMap()
        if (fields.isNotEmpty()) { fieldErrors(fields); return }
        mutableState.value = current.copy(saving = true, error = null, fields = emptyMap())
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (draft.memberId == null) repository.create(input)
                    else repository.updatePermissions(draft.memberId, draft.permissions, requireNotNull(draft.expectedVersion))
                }
                val snapshot = current.snapshot
                val members = if (draft.memberId == null) snapshot.members + result
                    else snapshot.members.map { if (it.id == result.id) result else it }
                saved.remove<String>("teamDraft")
                mutableState.value = TeamUiState(loading = false, snapshot = snapshot.copy(members = members),
                    notice = if (draft.memberId == null) "Encargado agregado${if (snapshot.isDemo) " en este dispositivo" else ""}."
                    else "Permisos guardados${if (snapshot.isDemo) " en este dispositivo" else ""}.")
            } catch (e: CancellationException) { throw e
            } catch (e: TeamValidationException) { fieldErrors(e.fields)
            } catch (e: RanchAccessDeniedException) { denied()
            } catch (e: TeamConflictException) {
                mutableState.value = state.value.copy(saving = false,
                    error = "Los permisos cambiaron mientras editabas. Conservamos tus casillas; vuelve a abrir al encargado para revisar la versión actual.")
            } catch (e: Exception) {
                mutableState.value = state.value.copy(saving = false, error = "No pudimos guardar. Conservamos lo escrito para que lo intentes de nuevo.")
            }
        }
    }
    private fun fieldErrors(fields: Map<TeamField, String>) {
        mutableState.value = state.value.copy(saving = false, fields = fields, error = "Revisa los campos marcados.",
            validationAttempt = state.value.validationAttempt + 1)
    }
    private fun denied() {
        saved.remove<String>("teamDraft")
        mutableState.value = TeamUiState(loading = false, error = "Solo el productor puede administrar el equipo de este rancho.")
    }
    companion object {
        val Factory = viewModelFactory { initializer {
            val app = this[APPLICATION_KEY] as AreteApplication
            TeamViewModel(app.container.teamRepository, createSavedStateHandle())
        } }
    }
}
