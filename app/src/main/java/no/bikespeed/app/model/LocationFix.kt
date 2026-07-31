package no.bikespeed.app.model

import java.time.Instant

/**
 * A platform-free GPS fix — the one shape every service-layer consumer speaks.
 *
 * Where the platform APIs use sentinels (CoreLocation's negative accuracies, Android's
 * `hasSpeed()`/`hasBearing()` flags), this type uses nulls. The `Location → LocationFix`
 * mapper in the platform layer is the **single** place that translation happens; everything
 * downstream can rely on "null means unknown" without knowing either platform's convention.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    /** Meters; always present — a fix without one is not a fix. */
    val horizontalAccuracy: Double,
    /** Meters, or null when the fix carries no altitude. */
    val altitude: Double?,
    /** Meters, or null when the altitude (if any) has no trustworthy error bar. */
    val verticalAccuracy: Double?,
    /** m/s, or null when unknown. Never negative. */
    val speed: Double?,
    /** m/s, or null when unknown. */
    val speedAccuracy: Double?,
    /** Degrees clockwise from true north, or null when unknown. */
    val course: Double?,
    /** Degrees, or null when unknown. */
    val courseAccuracy: Double?,
    val timestamp: Instant,
) {

    /**
     * The fix's altitude when it can be trusted, null otherwise. A missing vertical accuracy
     * is "no altitude"; above [ceiling] the reading exists but its error bar dwarfs the
     * measurement — an accumulator banking every 3 m move can't take a 30 m error bar. Every
     * consumer of altitude goes through here so the rule can't drift apart between height
     * profile, climb totals and live readout.
     */
    fun usableAltitude(ceiling: Double = Double.MAX_VALUE): Double? {
        val verticalAccuracy = verticalAccuracy ?: return null
        if (verticalAccuracy < 0 || verticalAccuracy > ceiling) return null
        return altitude
    }

    /** Great-circle distance to another fix, in meters. */
    fun distanceTo(other: LocationFix): Double =
        GeoMath.haversineMeters(latitude, longitude, other.latitude, other.longitude)
}
