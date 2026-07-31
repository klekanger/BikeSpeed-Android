package no.bikespeed.app.data

import kotlinx.serialization.json.Json
import no.bikespeed.app.model.AltitudeSample
import no.bikespeed.app.model.RouteSample

/**
 * The one encoding the payload blobs use: plain UTF-8 JSON arrays, iOS field names, ISO-8601
 * dates, absent (not `null`) optionals.
 *
 * That identity is load-bearing: it is the same wire shape the iOS app persists, so the
 * future shared REST backend can speak one format to both platforms without a migration.
 * `TripPayloadCoderTest` pins the exact JSON.
 */
object TripPayloadCoder {

    private val json = Json {
        // Absent, not "key": null — matching Swift's encodeIfPresent behaviour.
        explicitNulls = false
        // A future field added by iOS first must not break decoding here.
        ignoreUnknownKeys = true
    }

    fun encodeRoute(route: List<RouteSample>): ByteArray =
        json.encodeToString(route).toByteArray()

    fun decodeRoute(data: ByteArray): List<RouteSample> =
        json.decodeFromString(data.decodeToString())

    fun encodeAltitudeProfile(profile: List<AltitudeSample>): ByteArray =
        json.encodeToString(profile).toByteArray()

    fun decodeAltitudeProfile(data: ByteArray): List<AltitudeSample> =
        json.decodeFromString(data.decodeToString())
}
