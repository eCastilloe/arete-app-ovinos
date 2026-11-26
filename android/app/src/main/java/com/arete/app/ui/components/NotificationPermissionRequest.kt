package com.arete.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * HU-39: en Android 13+ pide el permiso de notificaciones una sola vez por instalación;
 * en versiones anteriores (como el emulador API 26) no hace falta pedirlo.
 * [onResult] recibe si la app puede notificar.
 */
@Composable
fun NotificationPermissionRequest(onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted)
    }
    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        when {
            !needsPermission -> onResult(true)
            // Se guarda en el celular para no volver a preguntar en cada arranque.
            !preferences.getBoolean(ASKED, false) -> {
                preferences.edit { putBoolean(ASKED, true) }
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> onResult(false)
        }
    }
}

// Mismo archivo que el historial de avisos, que ya está fuera del respaldo: en un celular
// nuevo el permiso se vuelve a pedir.
private const val PREFERENCES = "avisos"
private const val ASKED = "permiso_notificaciones_preguntado"
