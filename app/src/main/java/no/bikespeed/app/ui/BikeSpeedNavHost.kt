package no.bikespeed.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import no.bikespeed.app.AppContainer
import no.bikespeed.app.ui.main.MainScreen
import no.bikespeed.app.ui.settings.SettingsScreen

/** Type-safe destinations — three screens don't justify more machinery than this. */
@Serializable object MainDestination
@Serializable object SettingsDestination
@Serializable object TripLogDestination

@Composable
fun BikeSpeedNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = MainDestination) {
        composable<MainDestination> {
            MainScreen(
                container = container,
                onTripLog = { navController.navigate(TripLogDestination) },
                onSettings = { navController.navigate(SettingsDestination) },
            )
        }
        composable<SettingsDestination> {
            SettingsScreen(
                settings = container.settingsStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable<TripLogDestination> {
            // The trip log list lands in the next step; the destination exists so the
            // navigation shape is final.
            TripLogPlaceholder()
        }
    }
}

@Composable
private fun TripLogPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Trip log coming soon", color = Color.White)
    }
}
