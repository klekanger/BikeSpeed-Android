package no.bikespeed.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The locale to format numbers and units in: the app language, as the string resources
 * follow it. Not Locale.getDefault(), which on API 29–32 ignores the per-app-locale backport
 * — a screen open when the language changed would keep "km/h" under Norwegian strings. And
 * reading the configuration recomposes the caller when the language changes.
 */
@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]
