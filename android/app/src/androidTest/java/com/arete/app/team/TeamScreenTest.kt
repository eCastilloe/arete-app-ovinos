package com.arete.app.team

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arete.app.data.model.*
import com.arete.app.data.repository.*
import com.arete.app.ui.components.RanchActions
import com.arete.app.ui.screens.team.*
import com.arete.app.ui.theme.AreteTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TeamScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "hu12-test-${UUID.randomUUID()}.json")
    private val repo = DemoTeamRepository(file, { true })
    private lateinit var model: TeamViewModel
    private var backCalls = 0
    @After fun cleanup() { compose.runOnIdle { store.clear() }; file.delete() }

    private fun open(repository: TeamRepository = repo, saved: SavedStateHandle = SavedStateHandle(), scale: Float = 1f) {
        compose.runOnUiThread { model = TeamViewModel(repository, saved); store.put("team", model) }
        compose.setContent {
            val state by model.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                AreteTheme { TeamContent(state, model::add, model::edit, model::change, model::password,
                    model::save, model::cancel, model::load, { backCalls++ }) }
            }
        }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }
    private fun input(field: TeamField, value: String) = compose.onNodeWithTag("team-field-${field.name}").performScrollTo().performTextReplacement(value)
    private fun fill(email: String = "worker@example.test") {
        input(TeamField.NAME, "Encargado prueba")
        input(TeamField.EMAIL, email)
        input(TeamField.PASSWORD, "demo-only-123")
        compose.onNodeWithTag("team-field-PASSWORD").performImeAction()
    }
    private fun save() { compose.onNodeWithTag("team-save").performClick(); compose.waitUntil(10_000) { !model.state.value.saving } }

    @Test fun emptyFieldsShowErrorsAndKeepTypedInput() {
        open()
        compose.onNodeWithTag("team-add").performClick()
        input(TeamField.NAME, "Nombre conservado")
        compose.onNodeWithTag("team-field-NAME").performImeAction()
        compose.onNodeWithTag("team-field-NAME").assertIsNotFocused()
        save()
        assertEquals("Nombre conservado", model.state.value.draft?.name)
        compose.onNodeWithTag("team-field-error-EMAIL").performScrollTo().assertIsDisplayed()
        assertTrue(TeamField.PASSWORD in model.state.value.fields)
    }
    @Test fun createsMemberWithCheckboxPermissionsAndClearsPassword() {
        open()
        compose.onNodeWithTag("team-add").assertHeightIsAtLeast(56.dp).performClick()
        fill()
        compose.onNodeWithTag("team-permission-capture").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("team-save").assertHeightIsAtLeast(56.dp)
        save()
        compose.onNodeWithTag("team-notice").assertIsDisplayed()
        compose.onNodeWithText("Lectura · Captura").performScrollTo().assertIsDisplayed()
        assertEquals("", model.state.value.password)
        assertFalse(file.readText().contains("demo-only-123"))
        assertTrue(runBlocking { repo.loadTeam().members.single().permissions.capture })
    }
    @Test fun duplicateEmailCanBeCorrectedWithoutLosingOtherFields() {
        runBlocking { repo.create(NewTeamMember("Previo", "worker@example.test", "demo", RanchPermissions())) }
        open()
        compose.onNodeWithTag("team-add").performClick()
        fill(" WORKER@EXAMPLE.TEST ")
        save()
        compose.onNodeWithTag("team-field-error-EMAIL").performScrollTo().assertIsDisplayed()
        assertEquals("Encargado prueba", model.state.value.draft?.name)
        input(TeamField.EMAIL, "second@example.test")
        compose.onNodeWithTag("team-field-EMAIL").performImeAction()
        save()
        assertEquals(2, model.state.value.snapshot?.members?.size)
    }
    @Test fun editPersistsRevocationWithoutPasswordOrIdentityChanges() {
        val member = runBlocking { repo.create(NewTeamMember("Ana prueba", "ana@example.test", "demo", RanchPermissions(true, true, true, true))) }
        open()
        compose.onNodeWithTag("team-edit-${member.id}").performScrollTo().performClick()
        compose.onNodeWithTag("team-field-PASSWORD").assertDoesNotExist()
        compose.onNodeWithTag("team-permission-capture").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithTag("team-permission-edit").performScrollTo().performClick().assertIsOff()
        save()
        val stored = runBlocking { DemoTeamRepository(file, { true }).loadTeam().members.single() }
        assertEquals(member.id, stored.id)
        assertEquals(RanchPermissions(true, false, false, true), stored.permissions)
    }
    @Test fun temporarySaveFailurePreservesFormAndCanRetry() {
        var fail = true
        open(object : TeamRepository by repo {
            override suspend fun create(member: NewTeamMember): TeamMember {
                if (fail) { fail = false; throw IOException() }
                return repo.create(member)
            }
        })
        compose.onNodeWithTag("team-add").performClick(); fill(); save()
        compose.onNodeWithTag("team-error").performScrollTo().assertIsDisplayed()
        assertEquals("worker@example.test", model.state.value.draft?.email)
        assertEquals("demo-only-123", model.state.value.password)
        save()
        assertNull(model.state.value.draft)
    }
    @Test fun savedDraftRestoresNonSecretFieldsButNeverPassword() {
        val saved = SavedStateHandle()
        open(saved = saved)
        compose.onNodeWithTag("team-add").performClick(); fill()
        assertFalse(saved.get<String>("teamDraft").orEmpty().contains("demo-only-123"))
        lateinit var restored: TeamViewModel
        compose.runOnUiThread { restored = TeamViewModel(repo, saved); store.put("restored", restored) }
        compose.waitUntil(10_000) { !restored.state.value.loading }
        assertEquals("worker@example.test", restored.state.value.draft?.email)
        assertEquals("", restored.state.value.password)
    }
    @Test fun workerCannotOpenTeamEvenThroughDirectNavigation() {
        runBlocking {
            val member = repo.create(NewTeamMember("Worker", "worker@example.test", "demo", RanchPermissions()))
            repo.selectDemoUser(member.id)
        }
        open()
        compose.onNodeWithTag("team-add").assertDoesNotExist()
        compose.onNodeWithTag("team-error").assertTextContains("Solo el productor", substring = true)
    }
    @Test fun actionButtonsFollowSessionPermissionsAndDisappearImmediately() {
        var access by mutableStateOf(RanchAccess("worker", "Worker", "r", "Rancho", RanchRole.WORKER, RanchPermissions(true, true, false, false)))
        var weightCalls = 0
        compose.setContent { AreteTheme { Column { RanchActions(access, {}, {}, {}, { weightCalls++ }, {}) } } }
        compose.onNodeWithTag("action-team").assertDoesNotExist()
        compose.onNodeWithTag("action-export").assertDoesNotExist()
        compose.onNodeWithTag("action-birth").assertIsDisplayed()
        compose.onNodeWithTag("action-weight").performClick()
        assertEquals(1, weightCalls)
        compose.runOnIdle { access = access.copy(permissions = RanchPermissions(false, false, false, false)) }
        compose.onNodeWithTag("action-birth").assertDoesNotExist()
        compose.onNodeWithTag("action-weight").assertDoesNotExist()
        compose.onNodeWithTag("action-inventory").assertDoesNotExist()
    }
    @Test fun largeTextCanSaveAndReturnToHome() {
        open(scale = 1.5f)
        compose.onNodeWithTag("team-add").performScrollTo().performClick()
        fill()
        compose.onNodeWithTag("team-permission-export").performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        save()
        compose.onNodeWithTag("team-back").assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(1, backCalls)
    }
    @Test fun cancelAsksBeforeDiscardingChanges() {
        open()
        compose.onNodeWithTag("team-add").performClick()
        input(TeamField.NAME, "Borrador")
        compose.onNodeWithTag("team-field-NAME").performImeAction()
        compose.onNodeWithTag("team-back").performClick()
        compose.onNodeWithText("Seguir editando").performClick()
        assertEquals("Borrador", model.state.value.draft?.name)
        compose.onNodeWithTag("team-back").performClick()
        compose.onNodeWithText("Descartar").performClick()
        assertNull(model.state.value.draft)
        assertTrue(runBlocking { repo.loadTeam().members.isEmpty() })
    }
}
