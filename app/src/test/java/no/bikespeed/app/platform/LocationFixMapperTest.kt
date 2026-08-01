package no.bikespeed.app.platform

import android.location.Location
import android.os.SystemClock
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The one sentinel→null boundary. Every `has*()` flag must translate — a missed one either
 * feeds a bogus 0 into the filter pipeline or throws away a real reading.
 */
@RunWith(RobolectricTestRunner::class)
class LocationFixMapperTest {

    private fun fullLocation(): Location = Location("test").apply {
        latitude = 59.9139
        longitude = 10.7522
        accuracy = 5f
        altitude = 142.0
        verticalAccuracyMeters = 4f
        speed = 8f
        speedAccuracyMetersPerSecond = 1f
        bearing = 90f
        bearingAccuracyDegrees = 10f
        time = 1_760_000_000_000L
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    @Test
    fun `a fully populated location maps every field`() {
        val fix = fullLocation().toLocationFix()

        assertThat(fix.latitude).isEqualTo(59.9139)
        assertThat(fix.longitude).isEqualTo(10.7522)
        assertThat(fix.horizontalAccuracy).isWithin(0.001).of(5.0)
        assertThat(fix.altitude!!).isWithin(0.001).of(142.0)
        assertThat(fix.verticalAccuracy!!).isWithin(0.001).of(4.0)
        assertThat(fix.speed!!).isWithin(0.001).of(8.0)
        assertThat(fix.speedAccuracy!!).isWithin(0.001).of(1.0)
        assertThat(fix.course!!).isWithin(0.001).of(90.0)
        assertThat(fix.courseAccuracy!!).isWithin(0.001).of(10.0)
        // A brand-new fix (zero age in the monotonic domain) is stamped "now" on the device
        // clock — NOT with Location.time, which lives on the GPS clock. See the test below.
        // (Tolerance because the wall clock ticks between mapping and asserting.)
        val ageMillis = System.currentTimeMillis() - fix.timestamp.toEpochMilli()
        assertThat(ageMillis).isAtLeast(0L)
        assertThat(ageMillis).isLessThan(1_000L)
    }

    /**
     * `Location.time` is GPS/UTC-derived while the filter pipeline ages fixes against the
     * device clock — two domains Android documents as mutually inconsistent, so a device
     * clock a few seconds off GPS time would make *every* fix look stale and the app
     * permanently frozen. The mapper therefore measures the fix's age where it is exact
     * (elapsedRealtimeNanos, the monotonic clock both stamps share) and re-expresses the
     * timestamp as "device-clock now minus that age".
     */
    @Test
    fun `the timestamp comes from the monotonic clock so device-clock skew cannot stale every fix`() {
        val location = fullLocation().apply {
            // A device clock 30 s ahead of GPS time: Location.time reads 30 s in the past
            // even though the fix is only 2 s old.
            time = System.currentTimeMillis() - 30_000L
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - 2_000_000_000L
        }

        val fix = location.toLocationFix()

        val ageMillis = System.currentTimeMillis() - fix.timestamp.toEpochMilli()
        assertWithMessage("a 2 s old fix must age as 2 s, not as the 30 s clock skew")
            .that(ageMillis).isAtLeast(2_000L)
        assertThat(ageMillis).isLessThan(3_000L)
    }

    @Test
    fun `a missing speed maps to null, not zero`() {
        val location = Location("test").apply {
            latitude = 59.9139
            longitude = 10.7522
            accuracy = 5f
            time = 1_760_000_000_000L
        }

        val fix = location.toLocationFix()

        assertThat(fix.speed).isNull()
        assertThat(fix.speedAccuracy).isNull()
        assertThat(fix.course).isNull()
        assertThat(fix.courseAccuracy).isNull()
        assertThat(fix.altitude).isNull()
        assertThat(fix.verticalAccuracy).isNull()
    }

    /**
     * An altitude without a vertical accuracy exists but can't be trusted; the shared
     * usableAltitude rule then rejects it — same outcome as CoreLocation's negative sentinel.
     */
    @Test
    fun `an altitude without vertical accuracy is present but unusable`() {
        val location = Location("test").apply {
            latitude = 59.9139
            longitude = 10.7522
            accuracy = 5f
            altitude = 142.0
            time = 1_760_000_000_000L
        }

        val fix = location.toLocationFix()

        assertThat(fix.altitude).isEqualTo(142.0)
        assertThat(fix.verticalAccuracy).isNull()
        assertThat(fix.usableAltitude()).isNull()
    }

    /** A fix without any horizontal accuracy must land in the rejected band. */
    @Test
    fun `a missing horizontal accuracy maps to the rejected band`() {
        val location = Location("test").apply {
            latitude = 59.9139
            longitude = 10.7522
            time = 1_760_000_000_000L
        }

        assertThat(location.toLocationFix().horizontalAccuracy).isLessThan(0.0)
    }
}
