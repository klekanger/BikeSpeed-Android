package no.bikespeed.app.platform

import android.location.Location
import android.os.SystemClock
import java.time.Instant
import no.bikespeed.app.model.LocationFix

/**
 * `Location` → [LocationFix]: **the** sentinel-to-null boundary. Android signals missing
 * fields with `has*()` flags (where CoreLocation used negative sentinels); everything
 * downstream of this function speaks "null means unknown" and never sees either platform's
 * convention.
 */
fun Location.toLocationFix(): LocationFix = LocationFix(
    latitude = latitude,
    longitude = longitude,
    // A fix without a horizontal accuracy cannot be trusted at all; -1 lands in the POOR
    // band and the filter rejects it — the same outcome as CoreLocation's negative sentinel.
    horizontalAccuracy = if (hasAccuracy()) accuracy.toDouble() else -1.0,
    altitude = if (hasAltitude()) altitude else null,
    verticalAccuracy = if (hasVerticalAccuracy()) verticalAccuracyMeters.toDouble() else null,
    speed = if (hasSpeed()) speed.toDouble() else null,
    speedAccuracy = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond.toDouble() else null,
    course = if (hasBearing()) bearing.toDouble() else null,
    courseAccuracy = if (hasBearingAccuracy()) bearingAccuracyDegrees.toDouble() else null,
    // NOT Instant.ofEpochMilli(time): Location.time is GPS/UTC-derived and Android documents
    // it as inconsistent with the device clock the pipeline ages fixes against — a device
    // clock a few seconds off GPS time would make every fix fail the freshness gate and the
    // app look permanently frozen. The fix's age is exact in the monotonic domain (both
    // stamps come from the same boot clock), so re-express the timestamp as "device-clock
    // now minus that age"; downstream deltas between fix timestamps are unaffected.
    timestamp = Instant.now().minusNanos(SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos),
)
