package com.arete.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.arete.app.data.model.*

/** Missing callbacks mean a feature has not been integrated; no inactive or pretend actions. */
@Composable
fun RanchActions(access: RanchAccess?, onInventory: (() -> Unit)? = null, onTeam: (() -> Unit)? = null,
    onBirth: (() -> Unit)? = null, onWeight: (() -> Unit)? = null, onExport: (() -> Unit)? = null) {
    if (access == null) return
    if (access.allows(RanchAction.READ) && onInventory != null) AreteButton("Ver inventario", onInventory, Modifier.testTag("action-inventory"))
    if (access.allows(RanchAction.MANAGE_TEAM) && onTeam != null) AreteSecondaryButton("Mi equipo", onTeam, Modifier.testTag("action-team"))
    if (access.allows(RanchAction.CAPTURE)) {
        if (onBirth != null) AreteSecondaryButton("Registrar parto", onBirth, Modifier.testTag("action-birth"))
        if (onWeight != null) AreteSecondaryButton("Registrar pesaje", onWeight, Modifier.testTag("action-weight"))
    }
    if (access.allows(RanchAction.EXPORT) && onExport != null) AreteSecondaryButton("Generar Sábana", onExport, Modifier.testTag("action-export"))
}
