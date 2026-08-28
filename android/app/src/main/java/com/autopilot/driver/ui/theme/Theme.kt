package com.autopilot.driver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AutopilotColors = darkColorScheme(
    primary = Color(0xFF78E6D0),
    onPrimary = Color(0xFF0B161A),
    background = Color(0xFF0B161A),
    surface = Color(0xFF13262B),
    onSurface = Color.White,
)

@Composable
fun AutopilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AutopilotColors, content = content)
}