package com.arete.app.team

import com.arete.app.data.model.*
import com.arete.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TeamRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file() = java.io.File(temporary.root, "team.json")
    private fun repo() = DemoTeamRepository(file(), { true })
    private fun input(email: String = "worker@example.test", permissions: RanchPermissions = RanchPermissions()) =
        NewTeamMember("Encargado de prueba", email, "only-test-secret", permissions)

    @Test fun allWorkerPermissionCombinationsFollowIndependentFlags() {
        repeat(16) { mask ->
            val p = RanchPermissions(mask and 1 != 0, mask and 2 != 0, mask and 4 != 0, mask and 8 != 0)
            val access = RanchAccess("u", "Worker", "r", "Rancho", RanchRole.WORKER, p)
            assertEquals(p.read, access.allows(RanchAction.READ))
            assertEquals(p.capture, access.allows(RanchAction.CAPTURE))
            assertEquals(p.edit, access.allows(RanchAction.EDIT))
            assertEquals(p.export, access.allows(RanchAction.EXPORT))
            assertFalse(access.allows(RanchAction.MANAGE_TEAM))
        }
    }
    @Test fun onlyProducerCanManageTeam() {
        RanchRole.entries.forEach { role ->
            val access = RanchAccess("u", "User", "r", "Rancho", role, RanchPermissions(false, false, false, false))
            assertEquals(role == RanchRole.PRODUCER, access.allows(RanchAction.MANAGE_TEAM))
            if (role != RanchRole.WORKER) assertTrue(access.allows(RanchAction.CAPTURE))
        }
    }
    @Test fun validatesRequiredInputsAndEmailWithoutInventingServerPasswordPolicy() {
        val errors = validateNewMember(NewTeamMember(" ", "bad@", " ", RanchPermissions()))
        assertEquals(setOf(TeamField.NAME, TeamField.EMAIL, TeamField.PASSWORD), errors.keys)
        assertTrue(validateNewMember(input()).isEmpty())
    }
    @Test fun createsPersistentMemberWithoutPassword() = runBlocking {
        val member = repo().create(input(" WORKER@EXAMPLE.TEST "))
        assertEquals("worker@example.test", member.email)
        assertEquals(member, repo().loadTeam().members.single())
        assertFalse(file().readText().contains("only-test-secret"))
        assertFalse(file().readText().contains("password", true))
        assertFalse(input().toString().contains("only-test-secret"))
    }
    @Test fun rejectsDuplicateEmailsIgnoringCaseAndSpaces() = runBlocking {
        val repository = repo()
        repository.create(input())
        try { repository.create(input(" WORKER@EXAMPLE.TEST ")); fail("Duplicate accepted") }
        catch (e: TeamValidationException) { assertTrue(TeamField.EMAIL in e.fields) }
        assertEquals(1, repository.loadTeam().members.size)
    }
    @Test fun editingPreservesIdentityAndUpdatesVersion() = runBlocking {
        val repository = repo()
        val member = repository.create(input())
        val changed = repository.updatePermissions(member.id, RanchPermissions(true, true, true, false), member.version)
        assertEquals(member.id, changed.id)
        assertEquals(member.name, changed.name)
        assertEquals(member.email, changed.email)
        assertEquals(2, changed.version)
        assertEquals(changed, repo().loadTeam().members.single())
    }
    @Test fun rejectsStaleVersionsAndUnknownIds() = runBlocking {
        val repository = repo()
        val member = repository.create(input())
        repository.updatePermissions(member.id, RanchPermissions(), 1)
        try { repository.updatePermissions(member.id, RanchPermissions(edit = true), 1); fail("Stale edit accepted") }
        catch (_: TeamConflictException) { }
        try { repository.updatePermissions("foreign", RanchPermissions(), 1); fail("Unknown member accepted") }
        catch (_: RanchAccessDeniedException) { }
    }
    @Test fun workerCannotManageTeamEvenCallingRepositoryDirectly() = runBlocking {
        val repository = repo()
        val member = repository.create(input())
        repository.selectDemoUser(member.id)
        try { repository.loadTeam(); fail("Worker read team") } catch (_: RanchAccessDeniedException) { }
        try { repository.create(input("other@example.test")); fail("Worker created account") } catch (_: RanchAccessDeniedException) { }
        try { repository.updatePermissions(member.id, RanchPermissions(edit = true), 1); fail("Worker elevated permissions") } catch (_: RanchAccessDeniedException) { }
    }
    @Test fun permissionRevocationAppliesOnNextLocalRequest() = runBlocking {
        val owner = repo()
        val member = owner.create(input(permissions = RanchPermissions(true, true, true, true)))
        val worker = repo()
        worker.selectDemoUser(member.id)
        assertTrue(worker.currentAccess().allows(RanchAction.EDIT))
        owner.updatePermissions(member.id, RanchPermissions(false, false, false, false), 1)
        assertFalse(worker.currentAccess().allows(RanchAction.EDIT))
        assertFalse(worker.currentAccess().allows(RanchAction.READ))
    }
    @Test fun realSessionNeverReceivesLocalTeamOrProducerAccess() = runBlocking {
        repo().create(input())
        val real = DemoTeamRepository(file(), { false })
        try { real.loadTeam(); fail("Demo data exposed") } catch (_: TeamUnavailableException) { }
        assertNull(real.access.value)
        assertTrue(real.members.value.isEmpty())
    }
    @Test fun unreadablePermissionsClearPreviouslyGrantedAccess() = runBlocking {
        val repository = repo()
        repository.create(input())
        assertNotNull(repository.access.value)
        file().writeText("{invalid")
        try { repository.refreshAccess(); fail("Invalid permissions accepted") } catch (_: TeamUnavailableException) { }
        assertNull(repository.access.value)
    }
    @Test fun inventoryRechecksReadAndEditInsteadOfTrustingVisibility() = runBlocking {
        val repository = repo()
        val member = repository.create(input())
        repository.selectDemoUser(member.id)
        val guarded = PermissionInventoryRepository(InventoryRepository { DemoInventoryData.snapshot().copy(canEdit = true) }, repository)
        assertFalse(guarded.load().canEdit)
        repo().updatePermissions(member.id, RanchPermissions(read = false), 1)
        try { guarded.load(); fail("Read bypassed") } catch (_: RanchAccessDeniedException) { }
    }
    @Test fun inventoryRejectsMismatchedRanchFromSource() = runBlocking {
        val guarded = PermissionInventoryRepository(InventoryRepository { DemoInventoryData.snapshot().copy(ranchId = "foreign") }, repo())
        try { guarded.load(); fail("Foreign ranch exposed") } catch (_: RanchAccessDeniedException) { }
    }
    @Test fun animalWriteRechecksPermissionAfterItWasRevoked() = runBlocking {
        val owner = repo()
        val member = owner.create(input(permissions = RanchPermissions(edit = true)))
        val worker = repo()
        worker.selectDemoUser(member.id)
        val animals = DemoAnimalRepository(java.io.File(temporary.root, "animals.json"), { true })
        val guarded = PermissionAnimalEditorRepository(animals, worker)
        val context = guarded.loadEditor("animal-0")
        assertTrue(context.canEdit)
        owner.updatePermissions(member.id, RanchPermissions(), 1)
        try { guarded.save("animal-0", context.existing!!.form, context.existing.version); fail("Write bypassed") }
        catch (_: AnimalEditingDeniedException) { }
        assertFalse(java.io.File(temporary.root, "animals.json").exists())
    }
}
