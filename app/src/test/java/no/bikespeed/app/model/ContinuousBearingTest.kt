package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A compass bearing wraps at 360°, but a view animating a rotation lerps the degree figure
 * linearly — handed 359° then 1° it sweeps a half-turn through south. [ContinuousBearing]
 * unwinds the stream so consecutive angles never jump more than 180°, and the animation always
 * takes the short way. Every test here is that one arrow-whips-through-south bug, from a
 * different side.
 */
class ContinuousBearingTest {

    /** With nothing to unwind against, the first bearing is passed through untouched. */
    @Test
    fun `the first bearing is taken as-is`() {
        val bearing = ContinuousBearing()
        assertThat(bearing.update(137.0)).isWithin(0.001).of(137.0)
        assertThat(bearing.degrees).isWithin(0.001).of(137.0)
    }

    /**
     * **The whole reason the type exists.** Crossing north 359° → 1° must step *forward* by two
     * degrees to 361 (which renders as 1° but animates the short way), never backward to 1
     * through south.
     */
    @Test
    fun `crossing north upward steps forward past three-sixty`() {
        val bearing = ContinuousBearing()
        bearing.update(359.0)
        assertThat(bearing.update(1.0)).isWithin(0.001).of(361.0)
    }

    /**
     * The mirror case: crossing north the other way, 1° → 359°, must step *back* to -1, not
     * forward 358° through south.
     */
    @Test
    fun `crossing north downward steps back past zero`() {
        val bearing = ContinuousBearing()
        bearing.update(1.0)
        assertThat(bearing.update(359.0)).isWithin(0.001).of(-1.0)
    }

    /**
     * A run of crossings accumulates without bound — two laps north keep climbing rather than
     * snapping back — while every value still renders to the right compass heading (mod 360).
     */
    @Test
    fun `repeated crossings accumulate freely`() {
        val bearing = ContinuousBearing()
        bearing.update(350.0)
        bearing.update(10.0) // +20 → 370
        bearing.update(350.0) // -20 → 350
        bearing.update(10.0) // +20 → 370
        assertThat(bearing.degrees).isWithin(0.001).of(370.0)
        // 370° and 10° point the same way; the point is the *path*, not the endpoint.
        val onCompass = (bearing.degrees % 360.0 + 360.0) % 360.0
        assertThat(onCompass).isWithin(0.001).of(10.0)
    }

    /**
     * An ordinary step nowhere near the wrap point is left exactly as it is — the unwinding
     * must not perturb the common case.
     */
    @Test
    fun `an interior step is unchanged`() {
        val bearing = ContinuousBearing()
        bearing.update(90.0)
        assertThat(bearing.update(120.0)).isWithin(0.001).of(120.0)
    }

    /**
     * The 180° antipode is the tie-break boundary: a delta of exactly +180 is kept as forward
     * motion, so a half-turn is well-defined rather than oscillating on rounding.
     */
    @Test
    fun `the exact antipode steps forward`() {
        val bearing = ContinuousBearing()
        bearing.update(0.0)
        assertThat(bearing.update(180.0)).isWithin(0.001).of(180.0)
    }
}
