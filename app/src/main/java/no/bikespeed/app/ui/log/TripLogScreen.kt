package no.bikespeed.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import no.bikespeed.app.data.TripDataStack
import no.bikespeed.app.model.MeasurementSystem
import no.bikespeed.app.model.TripDateRange
import no.bikespeed.app.model.TripDurationFormatting
import no.bikespeed.app.model.TripLogEntry
import no.bikespeed.app.model.TripLogSummary
import no.bikespeed.app.model.TripMonth
import no.bikespeed.app.service.SettingsStore
import no.bikespeed.app.ui.theme.GaugePalette

/**
 * The saved-trip list, newest first, under the rider's lifetime totals and personal bests.
 * The date filter narrows the *list* only — the summary stays lifetime regardless. Rows are
 * scalar-only ([no.bikespeed.app.data.TripScalars]); tapping opens the detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripLogScreen(
    stack: TripDataStack,
    settings: SettingsStore,
    onTrip: (UUID) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scalars by stack.repository.observeScalars().collectAsState(initial = null)
    val measurementSystem by settings.measurementSystemFlow.collectAsState()
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()

    // Not persisted: it resets to ALL each time the log is opened, matching iOS.
    var dateRange by remember { mutableStateOf(TripDateRange.ALL) }
    var filterMenuOpen by remember { mutableStateOf(false) }

    val entries = scalars?.map { it.toEntry() } ?: emptyList()
    val sections = TripLogGrouping.sections(entries, dateRange, Instant.now(), zone)

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Trip Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = { filterMenuOpen = true },
                            modifier = Modifier.testTag("filterButton"),
                        ) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Filter trips",
                                tint = if (dateRange == TripDateRange.ALL) Color.White else GaugePalette.accent,
                            )
                        }
                        DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                            val labels = mapOf(
                                TripDateRange.ALL to "All",
                                TripDateRange.LAST_7_DAYS to "Last 7 days",
                                TripDateRange.LAST_30_DAYS to "Last 30 days",
                                TripDateRange.THIS_YEAR to "This year",
                            )
                            TripDateRange.entries.forEach { range ->
                                DropdownMenuItem(
                                    text = { Text(labels.getValue(range)) },
                                    onClick = {
                                        dateRange = range
                                        filterMenuOpen = false
                                    },
                                    trailingIcon = {
                                        if (dateRange == range) Text("✓")
                                    },
                                )
                            }
                        }
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
        when {
            scalars == null -> Box(modifier = Modifier.fillMaxSize().padding(padding))

            entries.isEmpty() -> EmptyState(
                text = "No trips logged yet",
                modifier = Modifier.fillMaxSize().padding(padding).testTag("emptyLog"),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    // Fed the *unfiltered* entries on purpose — the date filter narrows the
                    // list, never the lifetime summary.
                    TripLogSummarySection(
                        entries = entries,
                        measurementSystem = measurementSystem,
                        locale = locale,
                    )
                }

                if (sections.isEmpty()) {
                    // The log isn't empty, this window is. Keep the summary and the filter
                    // reachable so the rider can widen the range rather than think trips
                    // vanished.
                    item {
                        EmptyState(
                            text = "No trips in this period — try a wider date range.",
                            modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("emptyRange"),
                        )
                    }
                } else {
                    sections.forEach { section ->
                        item(key = "month-${section.month.year}-${section.month.month}") {
                            Text(
                                text = monthTitle(section.month, zone, locale),
                                color = Color(0xFF9E9E9E),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(section.trips, key = { it.id }) { entry ->
                            TripRow(
                                entry = entry,
                                measurementSystem = measurementSystem,
                                locale = locale,
                                onClick = { onTrip(entry.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripRow(
    entry: TripLogEntry,
    measurementSystem: MeasurementSystem,
    locale: Locale,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale)
                .withZone(ZoneId.systemDefault())
                .format(entry.startDate),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RowStat(Icons.Filled.Route, measurementSystem.formattedDistance(entry.distance, locale))
            RowStat(Icons.Filled.Schedule, TripDurationFormatting.formatted(entry.duration, locale))
        }
    }
}

@Composable
private fun RowStat(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = GaugePalette.accent, modifier = Modifier.size(16.dp))
        Text(text = value, color = Color(0xFF9E9E9E), fontSize = 14.sp)
    }
}

/** Totals ↔ personal bests, switched by a segmented control. */
@Composable
private fun TripLogSummarySection(
    entries: List<TripLogEntry>,
    measurementSystem: MeasurementSystem,
    locale: Locale,
) {
    var pane by remember { mutableStateOf(0) }

    // Calendar facts come from the device region, not the app language: where the week
    // starts is about where the rider lives.
    val summary = TripLogSummary(
        entries = entries,
        firstDayOfWeek = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek,
        zone = ZoneId.systemDefault(),
        now = Instant.now(),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Totals", "Personal bests").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = pane == index,
                    onClick = { pane = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
            }
        }

        if (pane == 0) {
            SummaryRow(Icons.Filled.DirectionsBike, "Rides", summary.allTime.rideCount.toString())
            SummaryRow(Icons.Filled.Route, "Total distance", measurementSystem.formattedDistance(summary.allTime.distance, locale))
            SummaryRow(Icons.Filled.Schedule, "Total time", TripDurationFormatting.formatted(summary.allTime.duration, locale))
            // Hidden, not "0 m", when no ride ever recorded a climb.
            if (summary.allTime.ascent > 0) {
                SummaryRow(Icons.Filled.Terrain, "Total ascent", measurementSystem.formattedAltitude(summary.allTime.ascent, locale))
            }
            SummaryRow(Icons.Filled.CalendarMonth, "This week", measurementSystem.formattedDistance(summary.thisWeek.distance, locale))
            SummaryRow(Icons.Filled.CalendarMonth, "This month", measurementSystem.formattedDistance(summary.thisMonth.distance, locale))
        } else {
            summary.personalBests.longestRide?.let {
                SummaryRow(Icons.Filled.Route, "Longest ride", measurementSystem.formattedDistance(it.distance, locale))
            }
            summary.personalBests.fastestAverage?.let {
                SummaryRow(Icons.Filled.Speed, "Fastest average", measurementSystem.formattedSpeed(it.averageSpeed, locale = locale))
            }
            summary.personalBests.highestMaxSpeed?.let {
                SummaryRow(Icons.Filled.Bolt, "Top speed", measurementSystem.formattedSpeed(it.maxSpeed, locale = locale))
            }
            // Absent, not zero, when nothing in the log ever recorded altitude.
            summary.personalBests.biggestClimb?.totalAscent?.let {
                SummaryRow(Icons.Filled.Terrain, "Biggest climb", measurementSystem.formattedAltitude(it, locale))
            }
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, title: String, value: String) {
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

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = text, color = Color(0xFF9E9E9E))
    }
}

private fun monthTitle(month: TripMonth, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        .withZone(zone)
        .format(month.representativeDate(zone))
