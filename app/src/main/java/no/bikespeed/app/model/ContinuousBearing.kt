package no.bikespeed.app.model

/**
 * Unwinds a wrapping compass bearing (0..<360) into a continuous, ever-accumulating angle, so
 * a view animating a rotation crosses north the short way round instead of spinning back
 * through south.
 *
 * This is a *different* wrap-around problem from [CircularMean]. That one is about *averaging*
 * two bearings on the circle; this one is about *interpolating* between two already-chosen
 * bearings for a view animation. An animation framework lerps the degree figure linearly:
 * handed 359° then 1°, it sweeps 359 → 180 → 1 — the arrow whips a full half-turn through
 * south every time the rider crosses north. Smoothing the source ([CircularMean]) cannot fix
 * this: 359° and 1° are already only two degrees apart on the compass, yet still 358 apart as
 * *numbers*, which is all the animation sees.
 *
 * The fix is to never hand the animation a value that jumps more than 180°. Each new bearing
 * is folded in as the *shortest* signed step from the last one, so the running total drifts
 * past 360° (or below 0°) freely — 359° → 361° rather than 359° → 1°. Rotating by 361° points
 * exactly where 1° does, but the animation between them takes the two-degree path it should.
 */
class ContinuousBearing {

    /**
     * The accumulated continuous angle in degrees. Unbounded on purpose: it may sit at 361 or
     * -5 or 720, all of which render identically to their mod-360 equivalent but animate the
     * short way.
     */
    var degrees: Double = 0.0
        private set

    private var seeded = false

    /** Folds in a bearing in 0..<360 and returns the continuous angle nearest the previous one. */
    fun update(bearing: Double): Double {
        if (!seeded) {
            // Nothing to unwind against: the first bearing is the answer, verbatim.
            degrees = bearing
            seeded = true
            return degrees
        }

        // IEEE `remainder` rounds the quotient to nearest, so it folds the raw gap into
        // [-180, 180] — exactly the shortest signed step. (Kotlin's `%` is a truncating
        // remainder and would NOT do this.) The running total then drifts past 360° (or
        // below 0°) rather than jumping the long way round.
        degrees += Math.IEEEremainder(bearing - degrees, 360.0)
        return degrees
    }
}
