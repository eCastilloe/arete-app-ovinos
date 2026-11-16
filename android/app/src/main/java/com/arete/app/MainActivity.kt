package com.arete.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arete.app.ui.navigation.AreteNavHost
import com.arete.app.ui.theme.AreteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val authRepository = (application as AreteApplication).container.authRepository

        setContent {
            AreteTheme {
                val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = null)
                AreteNavHost(isLoggedIn = isLoggedIn)
            }
        }
    }
}