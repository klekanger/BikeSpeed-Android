package no.bikespeed.app.ui.stats

import no.bikespeed.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import no.bikespeed.app.model.CompassDirection
import no.bikespeed.app.model.Coordinate
import no.bikespeed.app.model.MeasurementSystem
import no.bikespeed.app.model.TripDurationFormatting
import no.bikespeed.app.model.TripState
import no.bikespeed.app.ui.theme.GaugePalette

/**
 * What the trip has accumulated so far. A value, not loose arguments: distance and duration
 * are both Double, so nothing but the label tells them apart.
 */
data class TripStats(
    val state: TripState,
    /** m/s. */
    val averageSpeed: Double,
    /** m/s. */
    val maxSpeed: Double,
    /** Meters. */
    val distance: Double,
    /** Seconds of active (moving) time; auto-paused time is excluded. */
    val duration: Double,
    /** Meters; null until any usable altitude has arrived — not the same as 0. */
    val totalAscent: Double?,
    /** Meters, positive; null on the same terms as totalAscent. */
    val totalDescent: Double?,
    /** Rise/run, e.g. 0.05 for 5 %; null until the trip has ridden a window's worth. */
    val grade: Double?,
    val isAutoPaused: Boolean,
)

/**
 * Where the rider is right now, independent of any trip. Each null means that value hasn't
 * resolved yet rather than that it's zero.
 */
data class LocationReadout(
    /** Meters. */
    val altitude: Double?,
    val coordinate: Coordinate?,
    /** Degrees. */
    val course: Double?,
    /** Null until reverse geocoding resolves one. */
    val streetName: String?,
)

/**
 * The 2×2 stats grid under the gauge, in the same brushed-metal bezel as the speedometer.
 * Cells, clockwise from top-left: average speed, direction of travel, distance, altitude.
 * All but the direction cell hold further readouts (max speed, duration, climb, descent,
 * GPS position) reached by tap or swipe — see [AltitudeCellPage] for the one cell whose page
 * set isn't fixed.
 */
@Composable
fun StatsPanel(
    trip: TripStats,
    location: LocationReadout,
    measurementSystem: MeasurementSystem,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    var speedPage by rememberSaveable { mutableIntStateOf(0) }
    var distancePage by rememberSaveable { mutableIntStateOf(0) }

    // The altitude cell alone is held as the readout rather than as an index, because its
    // page set changes with the trip state — an index would point at a different readout
    // after a Start, and off the end of the array after a Reset.
    var altitudePage by rememberSaveable { mutableStateOf(AltitudeCellPage.ALTITUDE) }
    val altitudePages = AltitudeCellPage.pages(trip.state)
    // A Reset takes climb and descent away underneath whoever was reading one, so the page
    // the cell *shows* falls back to altitude while altitudePage keeps what the rider chose.
    val currentAltitudePage =
        if (altitudePages.contains(altitudePage)) altitudePage else AltitudeCellPage.ALTITUDE

    val cornerRadius = 24.dp
    val bezelWidth = 5.dp
    val dividerColor = Color.White.copy(alpha = 0.12f)
    val bottomRowInset = DEFAULT_PAGE_INDICATOR_INSET + bezelWidth

    val showingMaxSpeed = speedPage == 1
    val showingDuration = distancePage == 1

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(GaugePalette.faceCenter)
            .border(
                width = bezelWidth,
                brush = Brush.sweepGradient(GaugePalette.metallicBezel),
                shape = RoundedCornerShape(cornerRadius),
            ),
    ) {
        Row(modifier = Modifier.weight(1f)) {
            // Speed cell: average ↔ max.
            StatCell(
                icon = if (showingMaxSpeed) Icons.Filled.Bolt else Icons.Filled.Speed,
                value = measurementSystem.formattedSpeed(
                    metersPerSecond = if (showingMaxSpeed) trip.maxSpeed else trip.averageSpeed,
                    locale = locale,
                ),
                paging = StatCellPagingState(
                    page = speedPage,
                    names = listOf(stringResource(R.string.average_speed), stringResource(R.string.max_speed)),
                    onPageChange = { speedPage = it },
                ),
                modifier = Modifier.weight(1f),
            )

            VerticalDivider(dividerColor)

            // Direction cell: the street name takes over the caption line; until one resolves
            // the line stays blank — the arrow and compass letter carry the cell alone.
            StatCell(
                icon = Icons.Filled.Navigation,
                value = location.course?.let { compassAbbreviation(CompassDirection.fromCourse(it)) } ?: "--",
                caption = stringResource(R.string.direction_of_travel),
                captionContent = StatCellCaption.Data(location.streetName),
                iconRotationDegrees = location.course ?: 0.0,
                iconTint = if (location.course == null) Color(0xFF9E9E9E) else GaugePalette.accent,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(dividerColor)

        Row(modifier = Modifier.weight(1f)) {
            // Distance cell: distance ↔ duration; the "Auto-paused" caption lands here and
            // explains both, since these are the two stats an auto-pause freezes.
            StatCell(
                icon = if (showingDuration) Icons.Filled.Timer else Icons.Filled.Route,
                value = if (showingDuration) {
                    TripDurationFormatting.formatted(seconds = trip.duration, locale = locale)
                } else {
                    measurementSystem.formattedDistance(meters = trip.distance, locale = locale)
                },
                captionContent = if (trip.isAutoPaused) {
                    StatCellCaption.Fixed(stringResource(R.string.auto_paused))
                } else {
                    StatCellCaption.Name
                },
                iconTint = if (trip.isAutoPaused) Color(0xFF9E9E9E) else GaugePalette.accent,
                paging = StatCellPagingState(
                    page = distancePage,
                    names = listOf(stringResource(R.string.distance), stringResource(R.string.duration)),
                    onPageChange = { distancePage = it },
                ),
                pageIndicatorInset = bottomRowInset,
                modifier = Modifier.weight(1f),
            )

            VerticalDivider(dividerColor)

            // Altitude cell: altitude / climb / descent / position, the grade riding the
            // caption line when on the altitude page.
            val gradeCaption = formattedGrade(trip.grade, locale)
            StatCell(
                icon = currentAltitudePage.icon,
                value = altitudeCellValue(currentAltitudePage, trip, location, measurementSystem, locale),
                captionContent = if (currentAltitudePage == AltitudeCellPage.ALTITUDE && gradeCaption != null) {
                    StatCellCaption.Data(gradeCaption)
                } else {
                    StatCellCaption.Name
                },
                paging = StatCellPagingState(
                    page = altitudePages.indexOf(currentAltitudePage).coerceAtLeast(0),
                    names = altitudePages.map { it.displayName },
                    onPageChange = { altitudePage = altitudePages[it] },
                ),
                pageIndicatorInset = bottomRowInset,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val AltitudeCellPage.icon: ImageVector
    get() = when (this) {
        AltitudeCellPage.ALTITUDE -> Icons.Filled.Terrain
        AltitudeCellPage.CLIMB -> Icons.Filled.NorthEast
        AltitudeCellPage.DESCENT -> Icons.Filled.SouthEast
        AltitudeCellPage.POSITION -> Icons.Filled.MyLocation
    }

private val AltitudeCellPage.displayName: String
    @Composable get() = stringResource(
        when (this) {
            AltitudeCellPage.ALTITUDE -> R.string.altitude
            AltitudeCellPage.CLIMB -> R.string.climb
            AltitudeCellPage.DESCENT -> R.string.descent
            AltitudeCellPage.POSITION -> R.string.position
        }
    )

@Composable
private fun compassAbbreviation(direction: CompassDirection): String = stringResource(
    when (direction) {
        CompassDirection.N -> R.string.compass_n
        CompassDirection.NE -> R.string.compass_ne
        CompassDirection.E -> R.string.compass_e
        CompassDirection.SE -> R.string.compass_se
        CompassDirection.S -> R.string.compass_s
        CompassDirection.SW -> R.string.compass_sw
        CompassDirection.W -> R.string.compass_w
        CompassDirection.NW -> R.string.compass_nw
    }
)

@Composable
private fun altitudeCellValue(
    page: AltitudeCellPage,
    trip: TripStats,
    location: LocationReadout,
    measurementSystem: MeasurementSystem,
    locale: Locale,
): String = when (page) {
    // "--" rather than "0 m" throughout: a trip with no altitude data hasn't measured a flat
    // ride, it hasn't measured anything.
    AltitudeCellPage.ALTITUDE ->
        location.altitude?.let { measurementSystem.formattedAltitude(it, locale) } ?: "--"
    AltitudeCellPage.CLIMB ->
        trip.totalAscent?.let { measurementSystem.formattedAltitude(it, locale) } ?: "--"
    AltitudeCellPage.DESCENT ->
        trip.totalDescent?.let { measurementSystem.formattedAltitude(it, locale) } ?: "--"
    AltitudeCellPage.POSITION -> formattedPosition(
        location.coordinate,
        north = stringResource(R.string.compass_n),
        south = stringResource(R.string.compass_s),
        east = stringResource(R.string.compass_e),
        west = stringResource(R.string.compass_w),
    )
}

/**
 * "▲ 4 %" climbing, "▼ 4 %" descending, bare "0 %" on the flat. Rounded to whole percent (a
 * gradient's decimals are noise to a rider) *before* the arrow is chosen: the raw sign flaps
 * around zero on flat ground, and pairing it with a rounded magnitude would show a
 * self-contradictory "▼ 0 %".
 */
internal fun formattedGrade(grade: Double?, locale: Locale): String? {
    if (grade == null) return null
    val wholePercent = (abs(grade) * 100).roundToInt()
    val magnitude = String.format(locale, "%d %%", wholePercent)
    if (wholePercent == 0) return magnitude
    val arrow = if (grade < 0) "▼" else "▲"
    return "$arrow $magnitude"
}

/**
 * Two lines: latitude then longitude, each with its hemisphere letter. The letters share the
 * compass abbreviations' localization keys, so they follow the same east/west swap in
 * Norwegian (Ø/V) once the l10n step lands.
 */
internal fun formattedPosition(
    coordinate: Coordinate?,
    north: String = "N",
    south: String = "S",
    east: String = "E",
    west: String = "W",
): String {
    if (coordinate == null) return "--"
    val latHemisphere = if (coordinate.latitude >= 0) north else south
    val lonHemisphere = if (coordinate.longitude >= 0) east else west
    val lat = String.format(Locale.ROOT, "%.5f°", abs(coordinate.latitude))
    val lon = String.format(Locale.ROOT, "%.5f°", abs(coordinate.longitude))
    return "$lat $latHemisphere\n$lon $lonHemisphere"
}

@Composable
private fun VerticalDivider(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(color),
    )
}

@Composable
private fun HorizontalDivider(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .height(1.dp)
            .fillMaxWidth()
            .background(color),
    )
}
