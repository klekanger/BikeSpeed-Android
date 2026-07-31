package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The band boundaries drive the signal dot on the gauge, and — through `isUsable` — whether a
 * fix is trusted at all. Both edges are pinned, since an off-by-one here either throws away
 * good fixes or lets noise into the trip.
 */
class GpsSignalQualityTest {

    @Test
    fun `signal quality buckets horizontal accuracy`() {
        val cases = listOf(
            5.0 to GpsSignalQuality.GOOD,
            10.0 to GpsSignalQuality.GOOD,
            11.0 to GpsSignalQuality.FAIR,
            30.0 to GpsSignalQuality.FAIR,
            31.0 to GpsSignalQuality.POOR,
            -1.0 to GpsSignalQuality.POOR, // negative is the "invalid" sentinel
        )
        for ((accuracy, expected) in cases) {
            val quality = GpsSignalQuality.fromHorizontalAccuracy(
                horizontalAccuracy = accuracy,
                goodWithin = 10.0,
                acceptableWithin = 30.0,
            )
            assertThat(quality).isEqualTo(expected)
        }
    }

    @Test
    fun `only poor signal is unusable`() {
        assertThat(GpsSignalQuality.GOOD.isUsable).isTrue()
        assertThat(GpsSignalQuality.FAIR.isUsable).isTrue()
        assertThat(GpsSignalQuality.POOR.isUsable).isFalse()
    }
}
