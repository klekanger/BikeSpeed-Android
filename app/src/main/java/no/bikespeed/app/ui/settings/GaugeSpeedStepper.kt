package no.bikespeed.app.ui.settings

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import no.bikespeed.app.model.MeasurementSystem

/**
 * The max-gauge-speed stepper's arithmetic, extracted pure. The value is *stored* canonically
 * in km/h and *stepped* in the display unit — and a unit switch can leave the current display
 * value off the 5-unit step grid (converted from the other unit). Rather than add a full step
 * to that odd value, snap in the pressed direction to the nearest step multiple, so the
 * displayed speed is always round.
 */
object GaugeSpeedStepper {

    enum class Direction { UP, DOWN }

    /** Returns the new canonical km/h after one stepper press. */
    fun step(
        currentCanonicalKmh: Double,
        system: MeasurementSystem,
        direction: Direction,
    ): Double {
        val currentValue = system.maxGaugeSpeedValue(fromCanonicalKmh = currentCanonicalKmh)
        val step = system.maxGaugeSpeedStep
        val quotient = currentValue / step
        val isOnStepGrid = abs(quotient.roundToLong() - quotient) < 0.001
        val steppedQuotient = when (direction) {
            Direction.UP -> if (isOnStepGrid) quotient.roundToLong() + 1.0 else ceil(quotient)
            Direction.DOWN -> if (isOnStepGrid) quotient.roundToLong() - 1.0 else floor(quotient)
        }
        val range = system.maxGaugeSpeedRange
        val roundedValue = (steppedQuotient * step).coerceIn(range.start, range.endInclusive)
        return system.canonicalKmh(fromMaxGaugeSpeedValue = roundedValue)
    }
}
