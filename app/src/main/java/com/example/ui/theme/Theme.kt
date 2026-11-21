package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val A2MiguelColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = Color.White,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    secondaryContainer = ObsidianCardElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldAdBlock,
    onTertiary = Color.Black,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianStroke,
    error = CrimsonBright,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = A2MiguelColorScheme,
        typography = Typography,
        content = content
    )
}
