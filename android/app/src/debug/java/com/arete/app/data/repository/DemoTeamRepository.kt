package com.arete.app.data.repository

import android.content.Context
import com.arete.app.data.local.TokenStorage
import com.arete.app.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Debug-only accounts for UI exercises. Passwords are checked for input, then discarded; no authentication. */
class DemoTeamRepository(private val file: File, private val isDemoSession: suspend () -> Boolean) : TeamRepository {
    private val gson = Gson()
    private val mutex = Mutex()
    private var selectedId: String? = null
    private val mutableAccess = MutableStateFlow<RanchAccess?>(null)
    override val access = mutableAccess.asStateFlow()
    private val mutableMembers = MutableStateFlow<List<TeamMember>>(emptyList())
    val members = mutableMembers.asStateFlow()

    override suspend fun refreshAccess() { mutex.withLock {
        if (!isDemoSession()) { selectedId = null; mutableAccess.value = null; mutableMembers.value = emptyList() }
        else publish(read())
    } }

    override suspend fun currentAccess(): RanchAccess = mutex.withLock { authorize(); publish(read()); mutableAccess.value ?: throw RanchAccessDeniedException() }

    override suspend fun loadTeam(): TeamSnapshot = mutex.withLock {
        val records = managerRecords()
        TeamSnapshot(DemoInventoryData.RANCH_ID, "Rancho de ejemplo", records, true)
    }

    override suspend fun create(member: NewTeamMember): TeamMember = mutex.withLock {
        val records = managerRecords()
        val errors = validateNewMember(member).toMutableMap()
        val email = normalizedTeamEmail(member.email)
        if (records.any { normalizedTeamEmail(it.email) == email }) errors[TeamField.EMAIL] = "Ese usuario ya existe. Escribe otro correo."
        if (errors.isNotEmpty()) throw TeamValidationException(errors)
        val result = TeamMember(UUID.randomUUID().toString(), DemoInventoryData.RANCH_ID, member.name.trim(), email, member.permissions)
        write(records + result)
        result
    }

    override suspend fun updatePermissions(id: String, permissions: RanchPermissions, expectedVersion: Int): TeamMember = mutex.withLock {
        val records = managerRecords()
        val old = records.find { it.id == id } ?: throw RanchAccessDeniedException()
        if (old.version != expectedVersion) throw TeamConflictException()
        val result = old.copy(permissions = permissions, version = old.version + 1)
        write(records.map { if (it.id == id) result else it })
        result
    }

    suspend fun selectDemoUser(id: String?) { mutex.withLock {
        authorize()
        val records = read()
        if (id != null && records.none { it.id == id }) throw RanchAccessDeniedException()
        selectedId = id
        publish(records)
    } }

    private suspend fun authorize() {
        if (!isDemoSession()) { mutableAccess.value = null; mutableMembers.value = emptyList(); selectedId = null; throw TeamUnavailableException() }
    }
    private suspend fun managerRecords(): List<TeamMember> {
        authorize()
        val records = read()
        publish(records)
        if (mutableAccess.value?.allows(RanchAction.MANAGE_TEAM) != true) throw RanchAccessDeniedException()
        return records
    }
    private fun publish(records: List<TeamMember>) {
        mutableMembers.value = records
        mutableAccess.value = if (selectedId == null) RanchAccess("demo-producer", "Productor de ejemplo",
            DemoInventoryData.RANCH_ID, "Rancho de ejemplo", RanchRole.PRODUCER, RanchPermissions(), true)
        else records.find { it.id == selectedId }?.let { RanchAccess(it.id, it.name, it.ranchId, "Rancho de ejemplo", RanchRole.WORKER, it.permissions, true) }
    }
    private fun read(): List<TeamMember> {
        if (!file.exists()) return emptyList()
        return try {
            (gson.fromJson(file.readText(), Array<TeamMember>::class.java)?.toList() ?: throw TeamUnavailableException())
                .filter { it.ranchId == DemoInventoryData.RANCH_ID }
        } catch (_: Exception) {
            mutableAccess.value = null
            mutableMembers.value = emptyList()
            throw TeamUnavailableException()
        }
    }
    private fun write(records: List<TeamMember>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(gson.toJson(records))
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        publish(records)
    }
}

object TeamRepositoryFactory {
    fun create(context: Context, tokens: TokenStorage): TeamRepository {
        val repository = DemoTeamRepository(File(context.filesDir, "hu12-demo/team.json"), { tokens.getToken() == "demo-token" })
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            tokens.tokenFlow.collect {
                try { repository.refreshAccess() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Fail closed; the UI offers retry. */ }
            }
        }
        return repository
    }
}
