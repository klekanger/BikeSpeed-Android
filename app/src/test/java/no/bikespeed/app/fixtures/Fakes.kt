package no.bikespeed.app.fixtures

import no.bikespeed.app.model.LocationFix
import no.bikespeed.app.service.AltitudeSource
import no.bikespeed.app.service.FixListener
import no.bikespeed.app.service.LocationSource

/**
 * A [LocationSource] whose fixes are pushed by hand. [emit] invokes every listener inline,
 * mirroring the production source's synchronous delivery — a test can assert on the very next
 * line.
 */
class FakeLocationSource : LocationSource {

    private val listeners = mutableListOf<FixListener>()

    /** What the trip has *requested*, which is what the tests assert against. */
    var backgroundUpdatesEnabled = false
        private set

    override fun addFixListener(listener: FixListener) {
        listeners.add(listener)
    }

    override fun setBackgroundUpdates(enabled: Boolean) {
        backgroundUpdatesEnabled = enabled
    }

    fun emit(fix: LocationFix) {
        // Synchronous, registration order — the contract TripManager depends on.
        listeners.forEach { it.onFix(fix) }
    }
}

/**
 * An [AltitudeSource] with hand-set availability and readings. [stopUpdates] clears the
 * reading, mirroring the real barometer wrapper's rebaselining on every restart.
 */
class FakeAltitudeSource(
    override var isAvailable: Boolean = false,
) : AltitudeSource {

    override var relativeAltitude: Double? = null

    var isUpdating = false
        private set

    override fun startUpdates() {
        if (!isAvailable) return
        isUpdating = true
    }

    override fun stopUpdates() {
        isUpdating = false
        relativeAltitude = null
    }
}
