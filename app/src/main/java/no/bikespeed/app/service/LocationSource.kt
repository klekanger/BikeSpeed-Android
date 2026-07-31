package no.bikespeed.app.service

import no.bikespeed.app.model.LocationFix

/** A synchronous consumer of accepted GPS fixes. */
fun interface FixListener {
    fun onFix(fix: LocationFix)
}

/**
 * What `TripManager` needs from the GPS: the stream of accepted fixes, and a way to keep them
 * arriving while backgrounded. `LocationManager` is the real implementation; tests substitute
 * a fake, so trip behaviour is asserted against what was *requested* rather than against the
 * platform location APIs.
 *
 * **Delivery is synchronous and in registration order, on the main thread — deliberately.**
 * The iOS original uses a Combine `PassthroughSubject`, which delivers synchronously; dozens
 * of trip tests assert distance on the line after feeding a fix, and the auto-pause ordering
 * depends on it. A `SharedFlow` is *not* equivalent (collector delivery is
 * dispatcher-scheduled), which is why this is a plain listener registry.
 */
interface LocationSource {
    /** Registers a listener invoked synchronously for every accepted fix. */
    fun addFixListener(listener: FixListener)

    /**
     * Ask for (or release) background location updates. On only while a trip is recording —
     * held permanently it would keep GPS awake and drain the battery whenever the app is
     * merely backgrounded. On Android this starts/stops the foreground recording service.
     */
    fun setBackgroundUpdates(enabled: Boolean)
}
