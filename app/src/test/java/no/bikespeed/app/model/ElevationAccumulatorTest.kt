package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The deadband is the whole design: noise must never add up to a climb, and a real climb must
 * be counted in full however slowly it is ridden.
 */
class ElevationAccumulatorTest {

    @Test
    fun `noise inside the deadband accumulates nothing`() {
        val accumulator = ElevationAccumulator(deadband = 1.0)
        for (reading in 0 until 200) {
            accumulator.add(altitude = 100 + if (reading % 2 == 0) 0.4 else -0.4)
        }
        assertThat(accumulator.ascent).isEqualTo(0.0)
        assertThat(accumulator.descent).isEqualTo(0.0)
    }

    @Test
    fun `a real climb accumulates its full height`() {
        val accumulator = ElevationAccumulator(deadband = 1.0)
        var altitude = 100.0
        while (altitude <= 150.0) {
            accumulator.add(altitude = altitude)
            altitude += 2.0
        }
        assertThat(accumulator.ascent).isWithin(0.001).of(50.0)
        assertThat(accumulator.descent).isEqualTo(0.0)
    }

    /**
     * A long drag climbed in steps each too small to cross the deadband must still register:
     * the anchor only moves when the threshold is crossed, so sub-deadband deltas keep
     * measuring against it until together they do.
     */
    @Test
    fun `a staircase of sub-deadband steps still registers`() {
        val accumulator = ElevationAccumulator(deadband = 1.0)
        var altitude = 100.0
        while (altitude <= 110.0) {
            accumulator.add(altitude = altitude)
            altitude += 0.5
        }
        assertThat(accumulator.ascent).isWithin(0.001).of(10.0)
    }

    @Test
    fun `climb and descent are recorded independently`() {
        val accumulator = ElevationAccumulator(deadband = 1.0)
        var altitude = 100.0
        while (altitude <= 120.0) {
            accumulator.add(altitude = altitude)
            altitude += 2.0
        }
        altitude = 120.0
        while (altitude >= 90.0) {
            accumulator.add(altitude = altitude)
            altitude -= 2.0
        }
        assertThat(accumulator.ascent).isWithin(0.001).of(20.0)
        assertThat(accumulator.descent).isWithin(0.001).of(30.0)
    }

    /**
     * What separates "a flat trip" (zero ascent) from "a trip with no altitude data at all"
     * (nothing worth saving) once the trip is written to the log.
     */
    @Test
    fun `records whether any altitude was ever sampled`() {
        val accumulator = ElevationAccumulator(deadband = 1.0)
        assertThat(accumulator.hasRecordedAltitude).isFalse()

        accumulator.add(altitude = 100.0)
        assertThat(accumulator.hasRecordedAltitude).isTrue()

        accumulator.reset()
        assertThat(accumulator.hasRecordedAltitude).isFalse()
    }

    /**
     * The re-baseline used across breaks in sampling (a pause, a standstill, a restarted
     * barometer): the readings on either side of a gap aren't comparable, so the difference
     * between them must anchor fresh rather than be banked as climb — but everything already
     * earned stays earned.
     */
    @Test
    fun `reanchoring forgets the reference but keeps the totals`() {
        val accumulator = ElevationAccumulator(deadband = 1.0)
        accumulator.add(altitude = 100.0)
        accumulator.add(altitude = 110.0)
        assertThat(accumulator.ascent).isWithin(0.001).of(10.0)

        accumulator.reanchor()

        accumulator.add(altitude = 50.0) // wildly discontinuous — must anchor, not bank -60 as descent
        assertThat(accumulator.descent).isEqualTo(0.0)
        assertWithMessage("re-baselining is not the same as never having data")
            .that(accumulator.hasRecordedAltitude).isTrue()

        accumulator.add(altitude = 56.0)
        assertWithMessage("the accumulator keeps working after a reanchor")
            .that(accumulator.ascent).isWithin(0.001).of(16.0)
    }

    @Test
    fun `reset clears the totals and the anchor`() {
        val accumulator = ElevationAccumulator(deadband = 1.0)
        var altitude = 100.0
        while (altitude <= 120.0) {
            accumulator.add(altitude = altitude)
            altitude += 2.0
        }

        accumulator.reset()

        assertThat(accumulator.ascent).isEqualTo(0.0)
        assertThat(accumulator.descent).isEqualTo(0.0)
    }
}
