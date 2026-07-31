package no.bikespeed.app.model

/**
 * Turns a stream of altitude readings into total ascent and descent, through a deadband.
 *
 * Naively summing positive deltas manufactures hundreds of phantom metres an hour out of
 * sensor noise. Instead each reading is measured against an anchor that moves only when the
 * difference crosses [deadband]: noise oscillating inside the band cancels against the fixed
 * point, while a real climb — however slowly it accumulates — eventually crosses the band, is
 * banked in full, and re-anchors.
 *
 * @param deadband How far altitude must move from the anchor before it's believed. Sized to
 * the source's noise: ~1 m for the barometer, ~3 m for the much noisier GPS fallback.
 */
class ElevationAccumulator(val deadband: Double) {

    /** Meters. */
    var ascent: Double = 0.0
        private set

    /** Meters, positive. */
    var descent: Double = 0.0
        private set

    /**
     * Distinguishes a flat trip (zero ascent) from no altitude data ever arriving — the saved
     * log entry stores null for the latter, not a misleading 0.
     */
    var hasRecordedAltitude: Boolean = false
        private set

    private var anchor: Double? = null

    fun add(altitude: Double) {
        hasRecordedAltitude = true
        val anchor = this.anchor
        if (anchor == null) {
            this.anchor = altitude
            return
        }
        val delta = altitude - anchor
        if (delta >= deadband) {
            ascent += delta
            this.anchor = altitude
        } else if (delta <= -deadband) {
            descent += -delta
            this.anchor = altitude
        }
    }

    /**
     * Forgets the reference point but keeps the totals. For breaks in sampling — a pause, a
     * long standstill, a stopped-and-restarted barometer — where readings across the gap
     * aren't comparable: whatever altitude did in between wasn't ridden, so the next reading
     * anchors fresh instead of banking the difference as climb. [hasRecordedAltitude]
     * deliberately survives (and is stored, not derived from the anchor, for this reason):
     * data did arrive, the trip merely re-baselined.
     */
    fun reanchor() {
        anchor = null
    }

    fun reset() {
        ascent = 0.0
        descent = 0.0
        anchor = null
        hasRecordedAltitude = false
    }
}
