package com.arete.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
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

        val authRepository = (application as AreteApplication).container.authRepository

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
            }
        }
    }
}
