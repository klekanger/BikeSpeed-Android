package no.bikespeed.app.service

/**
 * What `TripManager` needs from the barometer: whether one is delivering, its latest reading,
 * and a lifecycle matched to the trip's. Nothing to subscribe to — the trip *samples*
 * [relativeAltitude] at each accepted GPS fix, so ascent inherits the running/auto-pause
 * gating that guards distance. The platform barometer wrapper is the real implementation;
 * tests substitute a fake.
 */
interface AltitudeSource {
    /**
     * False where there is no pressure sensor. The trip then falls back to GPS altitude with
     * a wider deadband instead of waiting forever.
     */
    val isAvailable: Boolean

    /**
     * Meters relative to where updates started; null until the first reading, null again
     * after [stopUpdates]. Only deltas are consumed, so the arbitrary zero point never
     * matters.
     */
    val relativeAltitude: Double?

    /**
     * Runs for exactly the span of a recording — on at start/resume, off at pause/reset,
     * mirroring background location updates — so the sensor isn't held hot while idle.
     */
    fun startUpdates()

    fun stopUpdates()
}
