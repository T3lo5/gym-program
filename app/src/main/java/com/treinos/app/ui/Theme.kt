package com.treinos.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF6750A4)
private val PurpleDark = Color(0xFFD0BCFF)
private val Teal = Color(0xFF006A6A)

private val LightColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF002020),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFF8F7FC),
    surfaceVariant = Color(0xFFE8E5F0),
)

private val DarkColors = darkColorScheme(
    primary = PurpleDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFF80D6D6),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F4F),
    onSecondaryContainer = Color(0xFFCCE8E8),
)

@Composable
fun TreinosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
