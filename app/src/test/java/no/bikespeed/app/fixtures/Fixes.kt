package no.bikespeed.app.fixtures

import java.time.Instant
import no.bikespeed.app.model.LocationFix

/** Shared coordinates for location-driven tests. */
object Fix {
    const val OSLO_LATITUDE = 59.9139
    const val OSLO_LONGITUDE = 10.7522

    /**
     * A latitude [meters] north of [latitude] — the harness's straight-line road.
     *
     * One degree of latitude on GeoMath's sphere is 111 194.93 m; the divisor is deliberately
     * a shade *smaller*, so a "5 m" move measures ~5.004 m under the app's own distance
     * function. Landing slightly over the nominal figure keeps sampling-interval boundary
     * tests (two 5 m hops must cross a 10 m interval) off the floating-point knife edge —
     * with the exact divisor a hop measures 4.9999967 m and the boundary flips. The iOS
     * fixture has the same property by accident of WGS-84 vs its 111 320 figure; here it is
     * explicit. The ~0.1 % overshoot is far inside every distance tolerance asserted.
     */
    fun north(latitude: Double, meters: Double): Double = latitude + meters / 111_100.0
}

/**
 * A [LocationFix] builder where every filtered field defaults to a *good* fix — each test
 * then breaks exactly the one axis it is about.
 */
fun makeFix(
    latitude: Double = Fix.OSLO_LATITUDE,
    longitude: Double = Fix.OSLO_LONGITUDE,
    horizontalAccuracy: Double = 5.0,
    altitude: Double? = 100.0,
    verticalAccuracy: Double? = 5.0,
    speed: Double? = 8.0,
    speedAccuracy: Double? = 1.0,
    course: Double? = 90.0,
    courseAccuracy: Double? = 5.0,
    timestamp: Instant = Instant.now(),
): LocationFix = LocationFix(
    latitude = latitude,
    longitude = longitude,
    horizontalAccuracy = horizontalAccuracy,
    altitude = altitude,
    verticalAccuracy = verticalAccuracy,
    speed = speed,
    speedAccuracy = speedAccuracy,
    course = course,
    courseAccuracy = courseAccuracy,
    timestamp = timestamp,
)
