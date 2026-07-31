package no.bikespeed.app.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * One point of a trip's recorded track.
 *
 * Carries a [timestamp] (unlike [AltitudeSample], indexed by distance): GPX is a time series,
 * and a track without times can't be imported as a ride by Strava or Komoot.
 *
 * [speed] and [distance] are app-internal (the speed-profile chart), never written to GPX.
 * Both are additive to the stored JSON blob and optional, so trips saved before they shipped
 * decode with them null and `SpeedProfile` reconstructs them from the geometry.
 *
 * The JSON shape (field names, ISO-8601 timestamps, absent-not-null optionals) is pinned by
 * `TripPayloadCoderTest` — it is the wire format shared with iOS.
 */
@Serializable
data class RouteSample(
    val latitude: Double,
    val longitude: Double,
    /**
     * Absent, not zero, when the fix had no usable vertical accuracy — GPX omits `<ele>`
     * rather than claiming sea level.
     */
    val altitude: Double? = null,
    @Serializable(with = InstantIso8601Serializer::class)
    val timestamp: Instant,
    /**
     * The instantaneous GPS (Doppler) speed at this point, in m/s — the same reading the gauge
     * shows, which is more trustworthy than deriving speed from consecutive positions.
     * Optional (a fix can lack a usable speed): trips saved before it shipped decode with
     * null, and `SpeedProfile` fills that gap by deriving speed from the timestamps instead.
     */
    val speed: Double? = null,
    /**
     * The trip's accumulated distance at this point, in meters — the *same* value
     * [AltitudeSample.distance] records, so the speed and height profiles plot against an
     * identical X-axis. Storing it (rather than re-summing raw fix-to-fix distances) is what
     * keeps the two charts, and the trip's distance total, in agreement; it also carries the
     * jitter-floor/teleport guarding that raw summing would lose. Null on legacy trips, where
     * `SpeedProfile` falls back to a running haversine total.
     */
    val distance: Double? = null,
)
