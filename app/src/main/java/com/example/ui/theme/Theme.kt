package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FuturisticCyberColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF001B2E),
    primaryContainer = Color(0xFF003852),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = CyberTeal,
    onSecondary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF003D33),
    onSecondaryContainer = Color(0xFFA7F7E9),
    tertiary = CyberBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF0D2D68),
    onTertiaryContainer = Color(0xFFD6E3FF),
    background = CyberBackground,
    onBackground = CyberTextPrimary,
    surface = CyberSurface,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextSecondary,
    outline = CyberCardBorder,
    outlineVariant = Color(0xFF162D4A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent futuristic branding
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FuturisticCyberColorScheme,
        typography = Typography,
        content = content
    )
}
