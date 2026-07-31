package no.bikespeed.app.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle geometry in pure Kotlin.
 *
 * Deliberately not `android.location.Location.distanceBetween`: keeping the model and service
 * layers free of the Android framework is what lets the whole trip pipeline run as plain JVM
 * tests. The haversine result differs from the platform's ellipsoidal formula by well under
 * the GPS noise floor at riding distances.
 */
object GeoMath {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Great-circle distance between two coordinates, in meters. */
    fun haversineMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Double {
        val lat1 = Math.toRadians(fromLatitude)
        val lat2 = Math.toRadians(toLatitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(toLongitude - fromLongitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
