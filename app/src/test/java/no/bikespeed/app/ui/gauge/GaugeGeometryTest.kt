package no.bikespeed.app.ui.gauge

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The gauge's angle/tick math is the tested spec (the iOS Canvas owned it untested; here it's
 * extracted precisely so these pins exist). The 270° sweep from -135° with 12-o'clock as the
 * midpoint is the dial's identity — get an endpoint wrong and every ride reads wrong.
 */
class GaugeGeometryTest {

    @Test
    fun `the dial sweeps 270 degrees from -135`() {
        assertThat(GaugeGeometry.angleForFraction(0.0)).isWithin(0.001).of(-135.0)
        assertThat(GaugeGeometry.angleForFraction(1.0)).isWithin(0.001).of(135.0)
        assertWithMessage("half scale points straight up — the 12 o'clock midpoint convention")
            .that(GaugeGeometry.angleForFraction(0.5)).isWithin(0.001).of(0.0)
    }

    @Test
    fun `the needle clamps to the dial's ends`() {
        assertThat(GaugeGeometry.needleAngle(speed = -5.0, maxSpeed = 60.0)).isWithin(0.001).of(-135.0)
        assertThat(GaugeGeometry.needleAngle(speed = 200.0, maxSpeed = 60.0)).isWithin(0.001).of(135.0)
        assertThat(GaugeGeometry.needleAngle(speed = 30.0, maxSpeed = 60.0)).isWithin(0.001).of(0.0)
    }

    @Test
    fun `the major step is the finest nice number within ten divisions`() {
        assertThat(GaugeGeometry.majorStep(60.0)).isEqualTo(10.0) // 60/10 = 6 → first ≥ is 10
        assertThat(GaugeGeometry.majorStep(25.0)).isEqualTo(5.0)
        assertThat(GaugeGeometry.majorStep(200.0)).isEqualTo(20.0)
        assertThat(GaugeGeometry.majorStep(125.0)).isEqualTo(15.0) // 12.5 → 15
        assertWithMessage("beyond every candidate, the coarsest one holds")
            .that(GaugeGeometry.majorStep(5_000.0)).isEqualTo(100.0)
    }

    @Test
    fun `a 60 unit dial has 31 ticks with 7 majors`() {
        val ticks = GaugeGeometry.ticks(60.0)

        assertThat(ticks).hasSize(31) // minor step 2 → 0..60 inclusive
        assertThat(ticks.count { it.isMajor }).isEqualTo(7) // 0, 10, …, 60
        assertThat(ticks.first().fraction).isWithin(0.001).of(0.0)
        assertThat(ticks.last().fraction).isWithin(0.001).of(1.0)
    }

    /**
     * The step need not divide the maximum evenly: ticks are placed by their actual fraction,
     * so the dial simply ends between ticks — same as a real gauge.
     */
    @Test
    fun `a maximum off the step grid ends between ticks`() {
        val ticks = GaugeGeometry.ticks(65.0)

        assertWithMessage("no tick may sit beyond the top of the dial")
            .that(ticks.all { it.fraction <= 1.0 }).isTrue()
        assertThat(ticks.last().value).isAtMost(65.0)
    }

    @Test
    fun `danger ticks start at 85 percent of the scale`() {
        val ticks = GaugeGeometry.ticks(60.0)

        val firstDanger = ticks.first { it.isDanger }
        assertThat(firstDanger.fraction).isAtLeast(GaugeGeometry.DANGER_START_FRACTION)
        assertThat(ticks.last { !it.isDanger }.fraction).isLessThan(GaugeGeometry.DANGER_START_FRACTION)
    }

    @Test
    fun `points follow the up-is-zero clockwise convention`() {
        // 0° is straight up.
        val (xUp, yUp) = GaugeGeometry.pointOnDial(0f, 0f, 100f, 0.0)
        assertThat(xUp).isWithin(0.01f).of(0f)
        assertThat(yUp).isWithin(0.01f).of(-100f)

        // +90° is to the right (clockwise).
        val (xRight, yRight) = GaugeGeometry.pointOnDial(0f, 0f, 100f, 90.0)
        assertThat(xRight).isWithin(0.01f).of(100f)
        assertThat(yRight).isWithin(0.01f).of(0f)
    }

    @Test
    fun `tick labels are integers when whole and one decimal otherwise`() {
        assertThat(GaugeGeometry.formattedTickLabel(10.0)).isEqualTo("10")
        assertThat(GaugeGeometry.formattedTickLabel(12.5)).isEqualTo("12.5")
    }
}
