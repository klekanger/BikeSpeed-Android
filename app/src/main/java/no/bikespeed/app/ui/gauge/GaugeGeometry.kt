package no.bikespeed.app.ui.gauge

import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The gauge's angle and tick math, extracted pure so it can be pinned by JVM tests — the iOS
 * app has no gauge unit tests (its Canvas owns the math); here the geometry is the tested
 * spec and the Canvas only draws it.
 *
 * Convention (identical to iOS): the dial sweeps 270° from -135° to +135°, where 0° points
 * straight up (12 o'clock — the sweep's midpoint) and positive angles are clockwise. Points
 * are placed with `x = cx + r·sin(θ)`, `y = cy − r·cos(θ)`.
 */
object GaugeGeometry {

    const val START_ANGLE: Double = -135.0
    const val SWEEP_ANGLE: Double = 270.0
    const val DANGER_START_FRACTION: Double = 0.85
    const val MINOR_PER_MAJOR: Int = 5

    /**
     * Candidate major-tick spacings, ascending — always "nice" round numbers so labels read
     * as multiples of 5, 10, 25, etc. rather than of whatever maxSpeed happens to be.
     */
    val MAJOR_STEP_CANDIDATES: List<Double> = listOf(5.0, 10.0, 15.0, 20.0, 25.0, 50.0, 100.0)

    /** Upper bound on major-tick count, so the scale doesn't get too fine-grained at large maxSpeed. */
    const val MAX_MAJOR_DIVISIONS: Double = 10.0

    /** Dial angle in degrees for a fraction (0..1) of the scale. */
    fun angleForFraction(fraction: Double): Double = START_ANGLE + SWEEP_ANGLE * fraction

    /** Needle angle for a speed against the gauge maximum, clamped to the dial's ends. */
    fun needleAngle(speed: Double, maxSpeed: Double): Double {
        val fraction = (speed / maxSpeed).coerceIn(0.0, 1.0)
        return angleForFraction(fraction)
    }

    /**
     * The finest step (from [MAJOR_STEP_CANDIDATES]) that keeps the major-tick count at or
     * below [MAX_MAJOR_DIVISIONS]. The step need not evenly divide maxSpeed — ticks are
     * placed by their actual fraction of the scale, so the last one or two minor ticks before
     * the top of the dial may simply be omitted, same as on a real gauge.
     */
    fun majorStep(maxSpeed: Double): Double {
        val minStep = maxSpeed / MAX_MAJOR_DIVISIONS
        return MAJOR_STEP_CANDIDATES.firstOrNull { it >= minStep } ?: MAJOR_STEP_CANDIDATES.last()
    }

    data class Tick(
        val value: Double,
        val fraction: Double,
        val isMajor: Boolean,
        val isDanger: Boolean,
    ) {
        val angleDegrees: Double get() = angleForFraction(fraction)
    }

    /** Every tick on the dial for a given maximum, minors included. */
    fun ticks(maxSpeed: Double): List<Tick> {
        val step = majorStep(maxSpeed)
        val minorStep = step / MINOR_PER_MAJOR
        val totalMinorTicks = floor(maxSpeed / minorStep).toInt()
        return (0..totalMinorTicks).map { i ->
            val value = i * minorStep
            val fraction = value / maxSpeed
            Tick(
                value = value,
                fraction = fraction,
                isMajor = i % MINOR_PER_MAJOR == 0,
                isDanger = fraction >= DANGER_START_FRACTION,
            )
        }
    }

    /** A point at [radiusFraction]·radius along the dial angle, in the gauge's up-is-zero convention. */
    fun pointOnDial(
        centerX: Float,
        centerY: Float,
        radius: Float,
        angleDegrees: Double,
    ): Pair<Float, Float> {
        val radians = Math.toRadians(angleDegrees)
        return Pair(
            centerX + radius * sin(radians).toFloat(),
            centerY - radius * cos(radians).toFloat(),
        )
    }

    /** Integer when whole, one decimal otherwise (a 12.5 minor step exists at maxSpeed 125). */
    fun formattedTickLabel(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value)
}
