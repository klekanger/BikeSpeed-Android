package no.bikespeed.app.model

import java.time.Instant
import java.util.UUID

/**
 * A saved, completed trip — the *scalar* summary of one. All measurements in SI units
 * (meters, m/s, seconds), like the rest of the model layer; the view layer converts via
 * [MeasurementSystem] for display.
 *
 * The domain value, not the row: `TripEntity` is what Room persists, this is what the pure
 * code speaks — what [TripLogSummary] reduces, what the list draws, and what a future web
 * service would put on the wire unchanged.
 *
 * **The heavy per-trip payloads are deliberately not here.** The height profile and track are
 * hundreds-to-thousands of samples each and the list draws neither; they live as blobs on the
 * Room row, loaded off the main thread only when a trip's detail opens. Folding either back in
 * would put every trip's payload into every render of the list.
 */
data class TripLogEntry(
    val id: UUID,
    val startDate: Instant,
    /** Seconds, active duration only. */
    val duration: Double,
    /** Meters. */
    val distance: Double,
    /** m/s. */
    val averageSpeed: Double,
    /** m/s. */
    val maxSpeed: Double,
    /**
     * Null, not zero: a trip without usable altitude data has no answer to "how much did you
     * climb", and 0 would be indistinguishable from a genuinely flat ride.
     */
    val totalAscent: Double?,
    /** Meters, positive. */
    val totalDescent: Double?,
)
