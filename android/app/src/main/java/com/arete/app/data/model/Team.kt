package com.arete.app.data.model

import java.util.Locale

enum class RanchRole { PRODUCER, WORKER, ASSOCIATION }
enum class RanchAction { READ, CAPTURE, EDIT, EXPORT, MANAGE_TEAM }

data class RanchPermissions(val read: Boolean = true, val capture: Boolean = false,
    val edit: Boolean = false, val export: Boolean = false)

data class RanchAccess(val userId: String, val userName: String, val ranchId: String, val ranchName: String,
    val role: RanchRole, val permissions: RanchPermissions, val isDemo: Boolean = false) {
    fun allows(action: RanchAction): Boolean {
        if (action == RanchAction.MANAGE_TEAM) return role == RanchRole.PRODUCER
        if (role == RanchRole.PRODUCER || role == RanchRole.ASSOCIATION) return true
        return when (action) {
            RanchAction.READ -> permissions.read
            RanchAction.CAPTURE -> permissions.capture
            RanchAction.EDIT -> permissions.edit
            RanchAction.EXPORT -> permissions.export
            RanchAction.MANAGE_TEAM -> false
        }
    }
}

data class TeamMember(val id: String, val ranchId: String, val name: String, val email: String,
    val permissions: RanchPermissions, val version: Int = 1)
data class TeamSnapshot(val ranchId: String, val ranchName: String, val members: List<TeamMember>, val isDemo: Boolean)
enum class TeamField { NAME, EMAIL, PASSWORD }

// Password deliberately excluded from generated toString/equals, saved state and persistence models.
class NewTeamMember(val name: String, val email: String, val password: String, val permissions: RanchPermissions) {
    override fun toString() = "NewTeamMember(redacted)"
}

fun normalizedTeamEmail(email: String) = email.trim().lowercase(Locale.ROOT)
fun validateNewMember(member: NewTeamMember): Map<TeamField, String> = buildMap {
    if (member.name.isBlank()) put(TeamField.NAME, "Escribe el nombre del encargado.")
    if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(member.email.trim()))
        put(TeamField.EMAIL, "Escribe un correo válido.")
    if (member.password.isBlank()) put(TeamField.PASSWORD, "Escribe una contraseña inicial.")
}
