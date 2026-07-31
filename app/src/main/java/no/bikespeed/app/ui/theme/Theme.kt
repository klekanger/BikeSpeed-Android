package no.bikespeed.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * BikeSpeed is a fixed dark instrument panel: no light theme, no dynamic color.
 * The palette mirrors the iOS app — pure black background, orange accent,
 * near-black panel surfaces. Fleshed out properly in the gauge/theme step.
 */
private val BikeSpeedColorScheme = darkColorScheme(
    primary = Color(0xFFFF9500), // iOS .orange
    background = Color.Black,
    surface = Color(0xFF1B1B1B), // gauge face / stats panel
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun BikeSpeedTheme(content: @Composable () -> Unit) {
    // Deliberately ignores isSystemInDarkTheme(): the app is always dark.
    MaterialTheme(
        colorScheme = BikeSpeedColorScheme,
        content = content,
    )
}
