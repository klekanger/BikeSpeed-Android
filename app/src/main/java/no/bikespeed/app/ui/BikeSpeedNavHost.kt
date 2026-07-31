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
import no.bikespeed.app.ui.log.TripLogScreen
import no.bikespeed.app.ui.main.MainScreen
import no.bikespeed.app.ui.settings.SettingsScreen

/** Type-safe destinations — three screens don't justify more machinery than this. */
@Serializable object MainDestination
@Serializable object SettingsDestination
@Serializable object TripLogDestination
@Serializable data class TripDetailDestination(val tripId: String)

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
            TripLogScreen(
                stack = container.tripDataStack,
                settings = container.settingsStore,
                onTrip = { id -> navController.navigate(TripDetailDestination(id.toString())) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<TripDetailDestination> {
            // The detail screen (map, charts, GPX share) lands in the next step.
            DetailPlaceholder()
        }
    }
}

@Composable
private fun DetailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Trip detail coming soon", color = Color.White)
    }
}
