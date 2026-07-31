package no.bikespeed.app.data

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import no.bikespeed.app.model.AltitudeSample
import no.bikespeed.app.model.RouteSample
import org.junit.Test

/**
 * The blob encoding is the wire format shared with iOS — field names, ISO-8601 dates,
 * absent-not-null optionals. These pin the exact JSON, plus the two RouteSample round-trip
 * cases from the iOS suite (there they guard a Swift Codable trap; here they guard the same
 * outcome against kotlinx.serialization's default handling).
 */
class TripPayloadCoderTest {

    @Test
    fun `speed and distance survive the encode-decode round trip`() {
        val original = RouteSample(
            latitude = 59.9139,
            longitude = 10.7522,
            altitude = 142.5,
            timestamp = Instant.ofEpochSecond(1_760_000_000),
            speed = 7.25,
            distance = 1234.5,
        )

        val data = TripPayloadCoder.encodeRoute(listOf(original))
        val decoded = TripPayloadCoder.decodeRoute(data)

        assertThat(decoded).containsExactly(original)
        assertThat(decoded.first().speed!!).isWithin(0.001).of(7.25)
        assertThat(decoded.first().distance!!).isWithin(0.001).of(1234.5)
    }

    @Test
    fun `a legacy blob without speed or distance still decodes`() {
        // A track saved before per-point speed/distance shipped (or by an old iOS build):
        // JSON with neither key. It must decode with both null, not throw — otherwise
        // syncing would lose every pre-existing route.
        val legacyJson = """
            [{"latitude":59.9139,"longitude":10.7522,"altitude":100,"timestamp":"2025-10-09T12:00:00Z"}]
        """.trimIndent()

        val decoded = TripPayloadCoder.decodeRoute(legacyJson.toByteArray())

        assertThat(decoded).hasSize(1)
        assertThat(decoded.first().speed).isNull()
        assertThat(decoded.first().distance).isNull()
    }

    /** The golden shape: iOS field names, ISO-8601 UTC timestamps, null optionals absent. */
    @Test
    fun `the encoded json matches the shared wire format`() {
        val sample = RouteSample(
            latitude = 59.913868,
            longitude = 10.752245,
            altitude = 112.3,
            timestamp = Instant.parse("2026-07-04T12:34:56Z"),
            speed = 6.82,
            distance = 0.0,
        )

        val json = TripPayloadCoder.encodeRoute(listOf(sample)).decodeToString()

        assertThat(json).contains("\"latitude\":59.913868")
        assertThat(json).contains("\"longitude\":10.752245")
        assertThat(json).contains("\"altitude\":112.3")
        assertThat(json).contains("\"timestamp\":\"2026-07-04T12:34:56Z\"")
        assertThat(json).contains("\"speed\":6.82")
        assertThat(json).contains("\"distance\":0.0")
    }

    @Test
    fun `a null altitude is omitted rather than written as null`() {
        val sample = RouteSample(
            latitude = 59.9139,
            longitude = 10.7522,
            altitude = null,
            timestamp = Instant.parse("2026-07-04T12:34:56Z"),
        )

        val json = TripPayloadCoder.encodeRoute(listOf(sample)).decodeToString()

        assertThat(json).doesNotContain("altitude")
        assertThat(json).doesNotContain("null")
    }

    @Test
    fun `an altitude profile round-trips sample for sample`() {
        val profile = listOf(
            AltitudeSample(distance = 0.0, altitude = 112.3),
            AltitudeSample(distance = 25.1, altitude = 114.9),
        )

        val decoded = TripPayloadCoder.decodeAltitudeProfile(TripPayloadCoder.encodeAltitudeProfile(profile))

        assertThat(decoded).isEqualTo(profile)
    }

    /** A field added by iOS first must not break decoding here. */
    @Test
    fun `unknown keys are ignored`() {
        val futureJson = """
            [{"latitude":59.9,"longitude":10.7,"timestamp":"2026-07-04T12:00:00Z","heartRate":142}]
        """.trimIndent()

        val decoded = TripPayloadCoder.decodeRoute(futureJson.toByteArray())

        assertThat(decoded).hasSize(1)
    }
}
