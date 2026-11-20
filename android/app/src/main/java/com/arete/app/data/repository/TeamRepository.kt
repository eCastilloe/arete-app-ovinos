package com.arete.app.data.repository

import com.arete.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface TeamRepository {
    val access: StateFlow<RanchAccess?>
    suspend fun currentAccess(): RanchAccess
    suspend fun refreshAccess()
    suspend fun loadTeam(): TeamSnapshot
    suspend fun create(member: NewTeamMember): TeamMember
    suspend fun updatePermissions(id: String, permissions: RanchPermissions, expectedVersion: Int): TeamMember
}

class TeamUnavailableException : Exception()
class RanchAccessDeniedException : Exception()
class TeamValidationException(val fields: Map<TeamField, String>) : Exception()
class TeamConflictException : Exception()

class UnavailableTeamRepository : TeamRepository {
    override val access = MutableStateFlow<RanchAccess?>(null).asStateFlow()
    override suspend fun currentAccess(): RanchAccess = throw TeamUnavailableException()
    override suspend fun refreshAccess() = Unit
    override suspend fun loadTeam(): TeamSnapshot = throw TeamUnavailableException()
    override suspend fun create(member: NewTeamMember): TeamMember = throw TeamUnavailableException()
    override suspend fun updatePermissions(id: String, permissions: RanchPermissions, expectedVersion: Int): TeamMember = throw TeamUnavailableException()
}

/** Rechecks the session on each request; these local guards never replace server authorization. */
class PermissionInventoryRepository(private val source: InventoryRepository, private val team: TeamRepository) : InventoryRepository {
    override suspend fun load(): InventorySnapshot {
        val access = team.currentAccess()
        if (!access.allows(RanchAction.READ)) throw RanchAccessDeniedException()
        val snapshot = source.load()
        if (snapshot.ranchId != access.ranchId || team.currentAccess() != access) throw RanchAccessDeniedException()
        return snapshot.copy(canEdit = snapshot.canEdit && access.allows(RanchAction.EDIT))
    }
}

class PermissionAnimalEditorRepository(private val source: AnimalEditorRepository, private val team: TeamRepository) : AnimalEditorRepository {
    override suspend fun loadEditor(animalId: String?): AnimalEditorContext {
        val access = team.currentAccess()
        if (!access.allows(RanchAction.READ) || (animalId == null && !access.allows(RanchAction.EDIT))) throw RanchAccessDeniedException()
        val context = source.loadEditor(animalId)
        if (context.ranchId != access.ranchId || team.currentAccess() != access) throw RanchAccessDeniedException()
        return context.copy(canEdit = context.canEdit && access.allows(RanchAction.EDIT))
    }
    override suspend fun save(animalId: String?, form: AnimalForm, expectedVersion: Int?): AnimalRecord {
        val access = team.currentAccess()
        if (!access.allows(RanchAction.READ) || !access.allows(RanchAction.EDIT)) throw AnimalEditingDeniedException()
        // Verify the delegate is scoped to the currently authorized ranch before changing a record.
        val context = source.loadEditor(animalId)
        if (context.ranchId != access.ranchId || team.currentAccess() != access) throw AnimalEditingDeniedException()
        return source.save(animalId, form, expectedVersion)
    }
}
