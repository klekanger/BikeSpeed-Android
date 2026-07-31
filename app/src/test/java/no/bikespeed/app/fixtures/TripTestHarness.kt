package no.bikespeed.app.fixtures

import no.bikespeed.app.model.LocationFix
import no.bikespeed.app.service.SettingsStore
import no.bikespeed.app.service.TripManager

/**
 * Drives a [TripManager] the way a ride does: fixes arrive from the location source, and the
 * clock moves only when the test says so.
 *
 * [move] is the primary verb — it advances the clock, walks the rider north, and feeds in the
 * resulting fix, which keeps the tests reading as statements about riding rather than about
 * the location APIs.
 */
class TripTestHarness(
    autoPauseEnabled: Boolean = true,
    barometerAvailable: Boolean = false,
) {
    val clock = TestClock()
    val locationSource = FakeLocationSource()

    /**
     * Unavailable by default, so a plain harness rides the GPS-altitude fallback — the fixes
     * already carry an altitude — and only the barometer-specific tests opt in to the
     * barometer.
     */
    val altimeter = FakeAltitudeSource(isAvailable = barometerAvailable)

    val settings = SettingsStore(InMemorySharedPreferences()).also {
        it.autoPauseEnabled = autoPauseEnabled
    }

    val trip = TripManager(
        locationSource = locationSource,
        altimeter = altimeter,
        settings = settings,
        now = clock,
    )

    private var latitude = Fix.OSLO_LATITUDE

    /**
     * Advance time and move the rider, then deliver the fix that describes it.
     *
     * [speed] defaults to the speed the movement actually implies, which is what a real GPS
     * reports; pass `null` for the "unknown speed" case (the platform sentinel). Tests that
     * care about the *reported* speed differing from the *travelled* distance — the teleport
     * and clamping cases — override it.
     *
     * Mind the implied speed: `meters / seconds` must stay under the 120 km/h ceiling
     * (33.3 m/s) or the fix is discarded as a teleport and no distance accrues.
     * `move(meters = 50.0)` at the default one second is 180 km/h, and will be thrown away —
     * which is the guard working, not a bug.
     */
    fun move(
        meters: Double,
        seconds: Double = 1.0,
        speed: Double? = IMPLIED_SPEED,
        altitude: Double = 100.0,
        horizontalAccuracy: Double = 5.0,
        verticalAccuracy: Double? = 5.0,
    ) {
        clock.advance(seconds)
        latitude = Fix.north(latitude, meters)
        send(
            makeFix(
                latitude = latitude,
                altitude = altitude,
                horizontalAccuracy = horizontalAccuracy,
                verticalAccuracy = verticalAccuracy,
                speed = if (speed === IMPLIED_SPEED) meters / seconds else speed,
                timestamp = clock.now,
            )
        )
    }

    /**
     * Deliver a fix without moving or advancing time — used to drop the initial anchor, since
     * the first fix a trip ever sees only establishes where distance is measured *from*.
     */
    fun anchor() {
        send(makeFix(latitude = latitude, speed = 0.0, timestamp = clock.now))
    }

    fun send(fix: LocationFix) {
        locationSource.emit(fix)
    }

    private companion object {
        /**
         * Distinguishes "unspecified — use the implied speed" from an explicit `null`
         * ("unknown speed"). Declared as a boxed `Double?` so the default argument reuses
         * this exact instance and the `===` identity check in [move] holds — a primitive
         * would be re-boxed on every call.
         */
        val IMPLIED_SPEED: Double? = -987_654.321
    }
}
