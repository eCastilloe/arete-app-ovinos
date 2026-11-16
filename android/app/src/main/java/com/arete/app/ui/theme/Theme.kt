package com.arete.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AreteColorScheme = lightColorScheme(
    primary = Agave,
    onPrimary = White,
    secondary = Agave,
    onSecondary = White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkSecondary,
    outline = Ink,
    outlineVariant = Line,
    error = AlertButton,
    onError = White,
    errorContainer = AlertBackground,
    onErrorContainer = AlertText
)

@Composable
fun AreteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AreteColorScheme,
        typography = Typography,
        content = content
    )
}