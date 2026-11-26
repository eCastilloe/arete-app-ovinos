package com.arete.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * HU-39: en Android 13+ pide una vez el permiso de notificaciones. En versiones anteriores
 * (como el emulador API 26) no hace falta pedirlo. [onResult] recibe si se puede notificar.
 */
@Composable
fun NotificationPermissionRequest(onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted)
    }
    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        when {
            !needsPermission -> onResult(true)
            !asked -> {
                asked = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
