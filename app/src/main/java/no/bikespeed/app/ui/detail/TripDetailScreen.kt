package no.bikespeed.app.ui.detail

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import no.bikespeed.app.data.TripDataStack
import no.bikespeed.app.model.Coordinate
import no.bikespeed.app.model.TripDurationFormatting
import no.bikespeed.app.model.TripLogEntry
import no.bikespeed.app.service.SettingsStore
import no.bikespeed.app.ui.theme.GaugePalette

/**
 * Full detail for a single saved trip: scalars, the recorded track on a map, height and speed
 * profiles against a shared distance axis, GPX share and delete. The payloads decode
 * off-main via [TripDetailLoader]; nothing here re-reads the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    entry: TripLogEntry,
    loader: TripDetailLoader,
    stack: TripDataStack,
    settings: SettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val measurementSystem = settings.measurementSystem

    var loaded by remember { mutableStateOf<TripDetailLoader.Loaded?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(entry.id) {
        val trackName = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale)
            .withZone(zone).format(entry.startDate)
        // Fixed format on purpose — this names a file, so it must be stable and sortable,
        // not localized. The track name inside the GPX is the human-readable one.
        val fileName = "BikeSpeed-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ROOT)
            .withZone(zone).format(entry.startDate)
        loaded = loader.load(entry, trackName = trackName, fileName = fileName)
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zone)
                            .format(entry.startDate)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    loaded?.gpxFile?.let { gpx ->
                        IconButton(onClick = { shareGpx(context, gpx) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export GPX")
                        }
                    }
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Trip", tint = Color(0xFFFF3B30))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(Icons.Filled.CalendarMonth, "Date",
                    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale).withZone(zone).format(entry.startDate))
                InfoRow(Icons.Filled.Schedule, "Duration", TripDurationFormatting.formatted(entry.duration, locale))
                InfoRow(Icons.Filled.Route, "Distance", measurementSystem.formattedDistance(entry.distance, locale))
                InfoRow(Icons.Filled.Speed, "Average speed", measurementSystem.formattedSpeed(entry.averageSpeed, locale = locale))
                InfoRow(Icons.Filled.Bolt, "Max speed", measurementSystem.formattedSpeed(entry.maxSpeed, locale = locale))
                // Absent, not zero, on trips recorded without altitude data — a "0 m" row
                // would claim the ride was flat.
                entry.totalAscent?.let {
                    InfoRow(Icons.Filled.Terrain, "Total ascent", measurementSystem.formattedAltitude(it, locale))
                }
                entry.totalDescent?.let {
                    InfoRow(Icons.Filled.SouthEast, "Total descent", measurementSystem.formattedAltitude(it, locale))
                }
            }

            loaded?.let { detail ->
                if (detail.coordinates.size >= 2) {
                    SectionTitle("Route")
                    RouteMap(
                        coordinates = detail.coordinates,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    )
                }
            }

            SectionTitle("Height profile")
            ChartOrPlaceholder(
                loaded = loaded,
                points = loaded?.profile?.map { it.distance to it.altitude },
                emptyText = "No altitude data for this trip",
            )

            SectionTitle("Speed profile")
            ChartOrPlaceholder(
                loaded = loaded,
                // Y in the user's unit: the model stores m/s, the view converts. X is
                // distance, matching the height profile so the two charts share an axis.
                points = loaded?.speedProfile?.map { it.distance to measurementSystem.speedValue(it.speed) },
                emptyText = "No speed data for this trip",
            )

            Box(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this trip?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        scope.launch {
                            stack.delete(listOf(entry.id))
                            onBack()
                        }
                    },
                ) { Text("Delete Trip", color = Color(0xFFFF3B30)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RouteMap(coordinates: List<Coordinate>, modifier: Modifier = Modifier) {
    val points = remember(coordinates) { coordinates.map { LatLng(it.latitude, it.longitude) } }
    val cameraPositionState = rememberCameraPositionState()
    var mapLoaded by remember { mutableStateOf(false) }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
        onMapLoaded = { mapLoaded = true },
    ) {
        Polyline(points = points, color = GaugePalette.accent, width = 10f)
        Marker(state = MarkerState(position = points.first()), title = "Start")
        Marker(state = MarkerState(position = points.last()), title = "Finish")
    }

    // Frame the ride once the map is up: its bounding box plus padding, so the polyline
    // doesn't run along the frame edge.
    LaunchedEffect(mapLoaded, points) {
        if (!mapLoaded) return@LaunchedEffect
        val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 64))
    }
}

@Composable
private fun ChartOrPlaceholder(
    loaded: TripDetailLoader.Loaded?,
    points: List<Pair<Double, Double>>?,
    emptyText: String,
) {
    when {
        // Payloads still decoding. "No data" here would be a lie that corrects itself a
        // frame later, reading as a flicker.
        loaded == null -> Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = GaugePalette.accent) }

        points.isNullOrEmpty() || points.size < 2 -> Text(
            text = emptyText,
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp,
        )

        else -> DistanceChart(
            points = points,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = GaugePalette.accent,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = GaugePalette.accent, modifier = Modifier.size(20.dp))
        Text(text = title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = Color(0xFF9E9E9E), fontSize = 14.sp)
    }
}

private fun shareGpx(context: android.content.Context, gpx: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", gpx)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export GPX"))
}
