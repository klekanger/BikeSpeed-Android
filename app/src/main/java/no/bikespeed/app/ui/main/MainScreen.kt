package no.bikespeed.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale
import no.bikespeed.app.AppContainer
import no.bikespeed.app.ui.gauge.SpeedometerGauge
import no.bikespeed.app.ui.stats.LocationReadout
import no.bikespeed.app.ui.stats.StatsPanel
import no.bikespeed.app.ui.stats.TripStats

/**
 * The home screen: gauge, 2×2 stats panel, trip controls, with the GPS indicator and
 * navigation cluster overlaid top-left/top-right — the same composition as the iOS
 * ContentView.
 */
@Composable
fun MainScreen(
    container: AppContainer,
    onTripLog: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tripManager = container.tripManager
    val locationManager = container.locationManager
    val settings = container.settingsStore

    // The trip clock's tick loop is NOT here: it lives at app scope (AppContainer starts
    // TripManager.startTicker), because a screen-owned driver dies when the rider opens
    // Settings or the trip log — taking the auto-pause watchdog with it mid-ride.

    // Live state.
    val displaySpeed by locationManager.displaySpeedFlow.collectAsState()
    val signalQuality by locationManager.signalQualityFlow.collectAsState()
    val altitude by locationManager.altitudeFlow.collectAsState()
    val coordinate by locationManager.coordinateFlow.collectAsState()
    val course by locationManager.courseFlow.collectAsState()
    val heading by locationManager.headingFlow.collectAsState()
    val isCourseLive by locationManager.isCourseLiveFlow.collectAsState()
    val streetName by container.addressLookupManager.streetNameFlow.collectAsState()

    val tripState by tripManager.stateFlow.collectAsState()
    val distance by tripManager.accumulatedDistanceFlow.collectAsState()
    val elapsed by tripManager.elapsedActiveDurationFlow.collectAsState()
    val maxSpeed by tripManager.maxSpeedFlow.collectAsState()
    val totalAscent by tripManager.totalAscentFlow.collectAsState()
    val totalDescent by tripManager.totalDescentFlow.collectAsState()
    val currentGrade by tripManager.currentGradeFlow.collectAsState()
    val isAutoPaused by tripManager.isAutoPausedFlow.collectAsState()

    val measurementSystem by settings.measurementSystemFlow.collectAsState()
    val maxGaugeSpeedKmh by settings.maxGaugeSpeedKmhFlow.collectAsState()
    val tilt by container.tiltProvider.tilt.collectAsState()

    val locale = Locale.getDefault()

    // Not `course`: it goes stale the moment the rider stops. travelDirection hands over to
    // the compass at a standstill — see LocationManager. Recomputed here from the collected
    // pieces so recomposition tracks it.
    val travelDirection = if (isCourseLive) course else (heading ?: course)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            SpeedometerGauge(
                speedMetersPerSecond = displaySpeed,
                maxGaugeSpeedKmh = maxGaugeSpeedKmh,
                measurementSystem = measurementSystem,
                locale = locale,
                tilt = tilt,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            StatsPanel(
                trip = TripStats(
                    state = tripState,
                    averageSpeed = if (elapsed > 0) distance / elapsed else 0.0,
                    maxSpeed = maxSpeed,
                    distance = distance,
                    duration = elapsed,
                    totalAscent = totalAscent,
                    totalDescent = totalDescent,
                    grade = currentGrade,
                    isAutoPaused = isAutoPaused,
                ),
                location = LocationReadout(
                    altitude = altitude,
                    coordinate = coordinate,
                    course = travelDirection,
                    streetName = streetName,
                ),
                measurementSystem = measurementSystem,
                locale = locale,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            TripControlBar(
                tripManager = tripManager,
                save = container.tripDataStack::save,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            GpsSignalIndicator(
                quality = signalQuality,
                isTracking = tripState == no.bikespeed.app.model.TripState.RUNNING && !isAutoPaused,
            )
            Spacer(modifier = Modifier.weight(1f))
            TopControlCluster(onTripLog = onTripLog, onSettings = onSettings)
        }
    }
}
