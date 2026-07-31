package no.bikespeed.app.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * An exponential moving average over a compass bearing.
 *
 * A bearing is an angle, not a number. Averaging 359° and 1° in degrees-space gives 180° —
 * due south — when the answer is due north, because the figures read as 358 apart and smooth
 * the long way round. On a bar-mounted phone that's an arrow that whips through south every
 * time the rider crosses north.
 *
 * The fix: smooth the *unit vector*, not the angle — keep an exponentially weighted
 * `(cos, sin)` and read the bearing back with `atan2`, so interpolation always takes the
 * short way round.
 *
 * (Different problem from the scalar display-speed EMA in `LocationManager`: speed has no
 * wrap-around.)
 *
 * @param smoothingFactor Weight given to each new bearing. Lower lags further behind, keeping
 * a jittering magnetometer from twitching the arrow at a standstill.
 */
class CircularMean(private val smoothingFactor: Double = 0.25) {

    /**
     * The smoothed unit vector. Null until the first bearing arrives — easing in from an
     * arbitrary zero would point the arrow at nothing real for the first second of every stop.
     */
    private var vectorX: Double? = null
    private var vectorY: Double = 0.0

    /** The smoothed bearing in 0..<360, or null before any bearing has been added. */
    val value: Double?
        get() = vectorX?.let { x -> degrees(x, vectorY) }

    /** Folds in a bearing and returns the new smoothed one. */
    fun add(degrees: Double): Double {
        val radians = Math.toRadians(degrees)
        val sampleX = cos(radians)
        val sampleY = sin(radians)

        val previousX = vectorX
        if (previousX == null) {
            // Nothing to average against: the first bearing is the answer.
            vectorX = sampleX
            vectorY = sampleY
            return degrees(sampleX, sampleY)
        }

        val smoothedX = smoothingFactor * sampleX + (1 - smoothingFactor) * previousX
        val smoothedY = smoothingFactor * sampleY + (1 - smoothingFactor) * vectorY
        vectorX = smoothedX
        vectorY = smoothedY
        return degrees(smoothedX, smoothedY)
    }

    fun reset() {
        vectorX = null
        vectorY = 0.0
    }

    /**
     * Back to a bearing, inverting the `(cos θ, sin θ)` encoding in [add]. The compass/`atan2`
     * axis mismatch (clockwise-from-north vs counter-clockwise-from-east) is harmless because
     * encode and decode agree and averaging is rotation-invariant. The normalization matters:
     * `atan2` answers in -π…π, and a consumer rotating an arrow must never be handed -3°.
     */
    private fun degrees(x: Double, y: Double): Double {
        val degrees = Math.toDegrees(atan2(y, x))
        return (degrees % 360.0 + 360.0) % 360.0
    }
}
