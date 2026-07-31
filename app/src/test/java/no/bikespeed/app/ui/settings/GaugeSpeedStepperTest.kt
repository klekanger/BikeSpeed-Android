package no.bikespeed.app.ui.settings

import com.google.common.truth.Truth.assertThat
import no.bikespeed.app.model.MeasurementSystem
import no.bikespeed.app.ui.settings.GaugeSpeedStepper.Direction
import org.junit.Test

/**
 * The stepper stores canonical km/h and steps in the display unit. The subtle case is the
 * unit switch: the converted value lands off the 5-unit grid, and a naive "+step" would keep
 * it odd forever — instead the press snaps to the nearest grid multiple in the pressed
 * direction, so the displayed speed is always round.
 */
class GaugeSpeedStepperTest {

    @Test
    fun `an on-grid metric value steps by five`() {
        val up = GaugeSpeedStepper.step(60.0, MeasurementSystem.METRIC, Direction.UP)
        val down = GaugeSpeedStepper.step(60.0, MeasurementSystem.METRIC, Direction.DOWN)

        assertThat(up).isWithin(0.001).of(65.0)
        assertThat(down).isWithin(0.001).of(55.0)
    }

    /**
     * 60 km/h displays as 37.28 mph — off the grid. A press up must land on 40 mph, not
     * 42.28; a press down on 35 mph.
     */
    @Test
    fun `an off-grid value after a unit switch snaps to the grid in the pressed direction`() {
        val canonical = 60.0 // km/h → 37.28 mph

        val up = GaugeSpeedStepper.step(canonical, MeasurementSystem.IMPERIAL, Direction.UP)
        val down = GaugeSpeedStepper.step(canonical, MeasurementSystem.IMPERIAL, Direction.DOWN)

        assertThat(MeasurementSystem.IMPERIAL.maxGaugeSpeedValue(up)).isWithin(0.001).of(40.0)
        assertThat(MeasurementSystem.IMPERIAL.maxGaugeSpeedValue(down)).isWithin(0.001).of(35.0)
    }

    @Test
    fun `stepping clamps to the range`() {
        val atTop = GaugeSpeedStepper.step(200.0, MeasurementSystem.METRIC, Direction.UP)
        val atBottom = GaugeSpeedStepper.step(20.0, MeasurementSystem.METRIC, Direction.DOWN)

        assertThat(atTop).isWithin(0.001).of(200.0)
        assertThat(atBottom).isWithin(0.001).of(20.0)
    }

    /** The display value is always a round grid multiple after any press, in either system. */
    @Test
    fun `every press lands on the display grid`() {
        var canonical = 60.0
        for (system in MeasurementSystem.entries) {
            canonical = GaugeSpeedStepper.step(canonical, system, Direction.UP)
            val display = system.maxGaugeSpeedValue(canonical)
            assertThat(display % system.maxGaugeSpeedStep).isWithin(0.001).of(0.0)
        }
    }
}
