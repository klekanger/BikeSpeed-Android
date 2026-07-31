package no.bikespeed.app.model

/**
 * The live gradient: rise over run across a trailing window of ridden distance.
 *
 * Never computed fix-to-fix — a ~5 m distance delta against ±1 m of altitude noise is
 * meaningless. Samples of (trip distance so far, altitude) queue up until they span
 * [windowDistance], and the grade is the slope between the window's two ends; older samples
 * fall off the back, so a hill crested a while ago stops being reported once it leaves the
 * window.
 *
 * @param windowDistance How much riding the slope is measured across. ~30 m: short enough to
 * feel live at bike speeds, long enough that altitude noise can't dominate the rise.
 */
class GradeCalculator(val windowDistance: Double) {

    private data class Sample(val distance: Double, val altitude: Double)

    private val samples = ArrayDeque<Sample>()

    /**
     * Null until a full window's worth of distance has been ridden — a made-up early reading
     * would be worse than none.
     */
    val grade: Double?
        get() {
            val first = samples.firstOrNull() ?: return null
            val last = samples.lastOrNull() ?: return null
            val run = last.distance - first.distance
            if (run < windowDistance) return null
            return (last.altitude - first.altitude) / run
        }

    /**
     * [distance] is the trip's accumulated distance at this reading — monotonic and unaffected
     * by pauses, the same X-axis convention as `AltitudeSample`.
     */
    fun add(distance: Double, altitude: Double) {
        samples.addLast(Sample(distance, altitude))
        // Trim to the oldest sample still needed to span the window, keeping one beyond it so
        // the run stays >= windowDistance rather than collapsing under it after every trim.
        while (samples.size > 2 && samples[1].distance <= samples.last().distance - windowDistance) {
            samples.removeFirst()
        }
    }

    fun reset() {
        samples.clear()
    }
}
