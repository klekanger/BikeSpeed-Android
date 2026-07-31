package no.bikespeed.app.model

import kotlinx.serialization.Serializable

/**
 * One point of a trip's height profile.
 *
 * [distance] (accumulated trip distance-so-far at sample time, not a timestamp) is monotonic
 * and unaffected by pause gaps — the natural X-axis for an elevation chart.
 *
 * Stored as a JSON blob on the Room row, not inline in [TripLogEntry]: only the detail view's
 * chart reads a profile, so the trip list mustn't decode one per row. No timestamp (unlike
 * [RouteSample]) — a chart against distance has no use for one.
 */
@Serializable
data class AltitudeSample(
    /** Meters. */
    val distance: Double,
    /** Meters. */
    val altitude: Double,
)
