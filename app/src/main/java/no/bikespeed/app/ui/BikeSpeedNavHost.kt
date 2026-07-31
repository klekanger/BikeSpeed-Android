package no.bikespeed.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.toRoute
import java.util.UUID
import no.bikespeed.app.model.TripLogEntry
import no.bikespeed.app.ui.detail.TripDetailLoader
import no.bikespeed.app.ui.detail.TripDetailScreen
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
        composable<TripDetailDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<TripDetailDestination>()
            val tripId = UUID.fromString(destination.tripId)
            val scalars by container.tripDataStack.repository.observeScalars()
                .collectAsState(initial = null)

            // Snapshot the entry the first time the row resolves — the iOS pattern: the
            // detail body reads a *value*, so deleting the row can't destroy it mid-pop.
            var entry by remember { mutableStateOf<TripLogEntry?>(null) }
            LaunchedEffect(scalars) {
                if (entry == null) {
                    entry = scalars?.firstOrNull { it.id == destination.tripId }?.toEntry()
                }
            }

            val context = LocalContext.current
            val loader = remember {
                TripDetailLoader(
                    repository = container.tripDataStack.repository,
                    cacheDir = context.cacheDir,
                )
            }

            entry?.let { snapshot ->
                TripDetailScreen(
                    entry = snapshot,
                    loader = loader,
                    stack = container.tripDataStack,
                    settings = container.settingsStore,
                    onBack = { navController.popBackStack() },
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    }
}
