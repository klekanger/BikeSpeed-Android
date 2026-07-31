package no.bikespeed.app.ui.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import no.bikespeed.app.model.AltitudeSample
import no.bikespeed.app.model.RouteSample
import no.bikespeed.app.model.TripLogEntry
import no.bikespeed.app.model.TripState
import no.bikespeed.app.service.TripManager
import no.bikespeed.app.ui.theme.GaugePalette

/**
 * Start/Pause/Resume + Save + Reset. The save function is injected (production passes
 * `TripDataStack::save`) so the failure path is testable without a database that can be made
 * to fail on cue.
 */
@Composable
fun TripControlBar(
    tripManager: TripManager,
    save: suspend (TripLogEntry, List<AltitudeSample>, List<RouteSample>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var showSavedConfirmation by remember { mutableStateOf(false) }
    var showSaveFailure by remember { mutableStateOf(false) }

    // Collected so this composable recomposes when the trip's state moves — the derived
    // canSave/canReset reads below are then fresh on every recomposition.
    val state by tripManager.stateFlow.collectAsState()
    val isAutoPaused by tripManager.isAutoPausedFlow.collectAsState()
    val distance by tripManager.accumulatedDistanceFlow.collectAsState()
    val elapsed by tripManager.elapsedActiveDurationFlow.collectAsState()
    // Reference the collected values so the reads are live; enablement still asks the manager.
    check(distance >= 0 && elapsed >= 0)

    /**
     * An auto-paused trip has already stopped accumulating, so "Pause" would read as a no-op
     * next to a panel saying "Auto-paused". The action still calls pause(), but here that
     * takes the pause off the app and hands it to the rider — the end-of-ride gesture, since
     * Save unlocks the moment the pause is manual. A Garmin labels the same button in the
     * same state "Stop", for the same reason.
     */
    val primaryLabel: String
    val primaryIcon: ImageVector
    if (isAutoPaused) {
        primaryLabel = "Stop"
        primaryIcon = Icons.Filled.Stop
    } else {
        when (state) {
            TripState.IDLE -> { primaryLabel = "Start"; primaryIcon = Icons.Filled.PlayArrow }
            TripState.RUNNING -> { primaryLabel = "Pause"; primaryIcon = Icons.Filled.Pause }
            TripState.PAUSED -> { primaryLabel = "Resume"; primaryIcon = Icons.Filled.PlayArrow }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = {
                when (state) {
                    TripState.IDLE -> tripManager.start()
                    TripState.RUNNING -> tripManager.pause()
                    TripState.PAUSED -> tripManager.resume()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = GaugePalette.accent,
                contentColor = Color.Black,
            ),
            modifier = Modifier.weight(1f).testTag("primaryButton"),
        ) {
            Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = " $primaryLabel", fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = {
                // **Reset comes after the write, not before.** reset() throws away the only
                // other copy of the ride, and the confirmation says it's safe — so neither
                // happens until the store says the trip is on disk. If the save fails the
                // trip is still paused and still theirs, and tapping Save again retries.
                val entry = tripManager.makeLogEntry() ?: return@OutlinedButton
                val profile = tripManager.altitudeProfile.toList()
                val route = tripManager.routeSamples.toList()
                scope.launch {
                    try {
                        save(entry, profile, route)
                        tripManager.reset()
                        showSavedConfirmation = true
                    } catch (_: Exception) {
                        showSaveFailure = true
                    }
                }
            },
            enabled = tripManager.canSaveTrip,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34C759)),
            modifier = Modifier.weight(1f).testTag("saveButton"),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = " Save", fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = { tripManager.reset() },
            enabled = tripManager.canResetTrip,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30)),
            modifier = Modifier.weight(1f).testTag("resetButton"),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = " Reset", fontSize = 15.sp)
        }
    }

    if (showSavedConfirmation) {
        AlertDialog(
            onDismissRequest = { showSavedConfirmation = false },
            confirmButton = {
                TextButton(onClick = { showSavedConfirmation = false }) { Text("OK") }
            },
            title = { Text("Trip saved") },
            modifier = Modifier.testTag("savedDialog"),
        )
    }
    if (showSaveFailure) {
        AlertDialog(
            onDismissRequest = { showSaveFailure = false },
            confirmButton = {
                TextButton(onClick = { showSaveFailure = false }) { Text("OK") }
            },
            title = { Text("Couldn't save trip") },
            text = { Text("Your ride hasn't been lost — it's still paused. Try saving again.") },
            modifier = Modifier.testTag("saveFailureDialog"),
        )
    }
}
