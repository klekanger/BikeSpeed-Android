package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import no.bikespeed.app.fixtures.makeFix
import org.junit.Test

/**
 * `usableAltitude` is the single altitude-trust rule every consumer shares — height profile,
 * climb totals and live readout must never drift apart on when an altitude counts.
 */
class LocationFixTest {

    @Test
    fun `an altitude with a tight error bar is usable`() {
        val fix = makeFix(altitude = 142.0, verticalAccuracy = 5.0)

        assertThat(fix.usableAltitude(ceiling = 15.0)).isEqualTo(142.0)
    }

    @Test
    fun `a missing vertical accuracy means no altitude`() {
        val fix = makeFix(altitude = 142.0, verticalAccuracy = null)

        assertThat(fix.usableAltitude()).isNull()
    }

    /**
     * The reading exists but its error bar dwarfs the measurement — an accumulator banking
     * every 3 m move can't take a 30 m error bar.
     */
    @Test
    fun `an error bar beyond the ceiling makes the altitude unusable`() {
        val fix = makeFix(altitude = 142.0, verticalAccuracy = 40.0)

        assertThat(fix.usableAltitude(ceiling = 15.0)).isNull()
        assertThat(fix.usableAltitude()).isEqualTo(142.0) // no ceiling → trusted
    }

    @Test
    fun `distance between two fixes is the haversine`() {
        val a = makeFix(latitude = 59.9139)
        val b = makeFix(latitude = 59.9139 + 50.0 / 111_320)

        assertThat(a.distanceTo(b)).isWithin(0.5).of(50.0)
    }
}
