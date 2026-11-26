package com.arete.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.arete.app.alerts.AlertScheduler
import com.arete.app.ui.components.NotificationPermissionRequest
import androidx.compose.runtime.getValue
import com.arete.app.ui.navigation.AreteNavHost
import com.arete.app.ui.theme.AreteTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AreteApplication).container
        val authRepository = container.authRepository

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                authRepository.isLoggedIn.collectLatest { loggedIn ->
                    if (loggedIn) authRepository.validateSession()
                }
            }
        }

        setContent {
            AreteTheme {
                val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = null)
                AreteNavHost(isLoggedIn = isLoggedIn)
                // HU-39: con sesión se revisan los avisos; al salir se quitan los del rancho.
                when (isLoggedIn) {
                    true -> NotificationPermissionRequest { granted -> if (granted) AlertScheduler.runNow(this) }
                    false -> LaunchedEffect(Unit) { container.alertRunner.clear() }
                    null -> Unit
                }
            }
        }
    }
}
