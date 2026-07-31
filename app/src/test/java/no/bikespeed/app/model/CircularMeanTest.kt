package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlin.math.min
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * A compass bearing is an angle, not a number: 359° and 1° are two degrees apart, but a plain
 * exponential mean over those figures reads them as 358 apart and smooths *the long way round*,
 * swinging the arrow through south every time the rider crosses north. Every test here is
 * really that one bug, from a different side.
 */
class CircularMeanTest {

    /**
     * With nothing to average against, the first bearing is the answer — an arrow that eased in
     * from an arbitrary zero would point at nothing real for the first second of every stop.
     */
    @Test
    fun `the first bearing is taken as-is`() {
        val mean = CircularMean(smoothingFactor = 0.25)

        assertThat(mean.add(137.0)).isWithin(0.001).of(137.0)
        val value = mean.value
        assertNotNull(value)
        assertThat(value!!).isWithin(0.001).of(137.0)
    }

    /**
     * **The whole reason the type exists.** Smoothing from 359° toward 1° must move *forwards*
     * through north — 359 → 0 → 1 — and never take the 358° journey backwards through south.
     */
    @Test
    fun `smoothing across north takes the short way round`() {
        val mean = CircularMean(smoothingFactor = 0.5)
        mean.add(359.0)

        val smoothed = mean.add(1.0)

        // Halfway from 359° to 1° is 0° — due north. Compared as a circular distance, so that
        // an answer of 360 (the same bearing, written differently) passes and 180 (the long
        // way round) cannot.
        assertThat(circularDistance(smoothed, 0.0)).isWithin(0.5).of(0.0)
    }

    /**
     * The same crossing, walked step by step: no intermediate value may ever land in the
     * southern half of the compass. A degrees-space mean would put every one of them there.
     */
    @Test
    fun `no step of a north crossing ever swings through south`() {
        val mean = CircularMean(smoothingFactor = 0.3)
        mean.add(350.0)

        repeat(20) {
            val smoothed = mean.add(10.0)
            assertWithMessage(
                "smoothing 350° toward 10° left the northern half at $smoothed° — it went the long way"
            ).that(circularDistance(smoothed, 0.0)).isAtMost(90.0)
        }

        val value = mean.value
        assertNotNull(value)
        assertThat(circularDistance(value!!, 10.0)).isWithin(1.0).of(0.0)
    }

    @Test
    fun `repeated bearings converge on that bearing`() {
        val mean = CircularMean(smoothingFactor = 0.4)
        mean.add(90.0)

        repeat(50) { mean.add(270.0) }

        val value = mean.value
        assertNotNull(value)
        assertThat(circularDistance(value!!, 270.0)).isWithin(0.5).of(0.0)
    }

    /**
     * A lower factor must lag further behind a new bearing than a higher one — that is what
     * "smoothing" means, and it's what keeps a jittering magnetometer from twitching the arrow.
     */
    @Test
    fun `a smaller factor lags further behind a new bearing`() {
        val sluggish = CircularMean(smoothingFactor = 0.1)
        val eager = CircularMean(smoothingFactor = 0.9)
        sluggish.add(0.0)
        eager.add(0.0)

        val sluggishStep = sluggish.add(90.0)
        val eagerStep = eager.add(90.0)

        assertThat(sluggishStep).isLessThan(eagerStep)
        assertThat(sluggishStep).isGreaterThan(0.0)
        assertThat(eagerStep).isLessThan(90.0)
    }

    /**
     * Output is a bearing, so it has to stay inside the compass — a consumer rotating an arrow
     * by -3° or 361° would be relying on the animation layer to be forgiving.
     */
    @Test
    fun `the smoothed bearing is always inside zero to three-sixty`() {
        val mean = CircularMean(smoothingFactor = 0.35)
        mean.add(1.0)

        var bearing = 350.0
        while (bearing <= 359.0) {
            val smoothed = mean.add(bearing)
            assertWithMessage("$smoothed° is not a bearing")
                .that(smoothed >= 0 && smoothed < 360).isTrue()
            bearing += 1.0
        }
    }

    @Test
    fun `reset forgets the bearing so the next one is taken as-is`() {
        val mean = CircularMean(smoothingFactor = 0.2)
        mean.add(10.0)

        mean.reset()

        assertThat(mean.value).isNull()
        assertThat(mean.add(200.0)).isWithin(0.001).of(200.0)
    }

    /**
     * Shortest angular distance between two bearings, 0…180. The comparison every assertion
     * above needs: in degrees-space 359 and 1 look 358 apart, which is exactly the mistake
     * under test.
     */
    private fun circularDistance(a: Double, b: Double): Double {
        val difference = abs(a - b) % 360.0
        return min(difference, 360.0 - difference)
    }
}
