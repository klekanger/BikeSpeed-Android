package no.bikespeed.app.service

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import no.bikespeed.app.fixtures.TestClock
import no.bikespeed.app.fixtures.makeFix
import no.bikespeed.app.model.GpsSignalQuality
import no.bikespeed.app.model.LocationFix
import org.junit.Test

/**
 * Pins LocationManager's two independent filters — accuracy and age — and the "freeze, don't
 * zero" policy they enforce. Everything downstream trusts an emitted fix completely, so these
 * are the rules that keep noise out of the gauge and out of the trip.
 */
class LocationManagerTest {

    private val clock = TestClock()
    private val manager = LocationManager(now = clock)

    private fun collectAccepted(): MutableList<LocationFix> {
        val accepted = mutableListOf<LocationFix>()
        manager.addFixListener { accepted.add(it) }
        return accepted
    }

    @Test
    fun `a good fix is accepted and published`() {
        val accepted = collectAccepted()

        manager.process(makeFix(speed = 8.0, timestamp = clock.now))

        assertThat(manager.hasFix).isTrue()
        assertThat(manager.rawSpeed).isWithin(0.01).of(8.0)
        assertThat(accepted).hasSize(1)
        assertThat(manager.coordinate).isNotNull()
    }

    // region Age filter

    /**
     * Subscribing replays the last cached fix, which can be minutes old while still carrying
     * excellent accuracy — so the accuracy filter waves it straight through. Everything
     * downstream reads a fix's timestamp as "now", so age has to be filtered separately.
     */
    @Test
    fun `a stale fix is dropped even when its accuracy is perfect`() {
        val accepted = collectAccepted()

        manager.process(
            makeFix(
                horizontalAccuracy = 1.0, // pristine
                timestamp = clock.now.minusSeconds(60), // and a minute out of date
            )
        )

        assertThat(accepted).isEmpty()
    }

    /**
     * A stale fix is not a signal-quality problem, so it must not flicker the GPS indicator
     * on its way to being ignored.
     */
    @Test
    fun `a stale fix leaves the fix indicator alone`() {
        manager.process(makeFix(timestamp = clock.now))
        check(manager.hasFix)

        manager.process(makeFix(timestamp = clock.now.minusSeconds(60)))

        assertWithMessage("a fix we chose to ignore must not report itself as a lost signal")
            .that(manager.hasFix).isTrue()
    }

    // endregion

    // region Accuracy filter

    @Test
    fun `an inaccurate fix is not published and drops the fix indicator`() {
        val accepted = collectAccepted()

        manager.process(makeFix(horizontalAccuracy = 40.0, timestamp = clock.now))

        assertThat(accepted).isEmpty()
        assertThat(manager.hasFix).isFalse()
        assertThat(manager.signalQuality).isEqualTo(GpsSignalQuality.POOR)
    }

    /**
     * The readouts hold their last trusted value rather than snapping to zero, so a moment of
     * bad signal reads as "unchanged", not as "you have stopped".
     */
    @Test
    fun `an inaccurate fix freezes the readouts rather than zeroing them`() {
        manager.process(makeFix(altitude = 150.0, speed = 8.0, timestamp = clock.now))

        manager.process(makeFix(altitude = 999.0, horizontalAccuracy = 40.0, speed = 0.0, timestamp = clock.now))

        assertThat(manager.rawSpeed).isWithin(0.01).of(8.0)
        assertThat(manager.altitude).isEqualTo(150.0)
    }

    // endregion

    // region Speed

    /**
     * The bug this deadband exists for: a parked bike doesn't report 0 m/s, it reports a few
     * tenths of one, and the gauge drew that as a needle creeping to 1–2 km/h and back. A
     * speed smaller than the fix's own error bar says nothing, so it must read as standing
     * still.
     */
    @Test
    fun `a speed inside its own error bar reads as standing still`() {
        manager.process(makeFix(speed = 0.4, speedAccuracy = 0.5, timestamp = clock.now))

        assertThat(manager.rawSpeed).isEqualTo(0.0)
    }

    /**
     * The reason the deadband tracks speedAccuracy rather than being one fixed width: indoors
     * and in street canyons the fix gets noisier and the phantom speed grows with it, and the
     * fix says so.
     */
    @Test
    fun `a noisier fix widens the deadband that silences it`() {
        manager.process(makeFix(speed = 0.9, speedAccuracy = 2.0, timestamp = clock.now))

        assertWithMessage("a 0.9 m/s reading with a 2 m/s error bar is not movement")
            .that(manager.rawSpeed).isEqualTo(0.0)
    }

    /**
     * And the reason it's capped: an error bar can be pessimistic enough to swallow a genuine
     * ride, and a rider actually doing 18 km/h must see 18 km/h no matter how little the
     * platform trusts itself.
     */
    @Test
    fun `a real riding speed survives a pessimistic error bar`() {
        manager.process(makeFix(speed = 5.0, speedAccuracy = 3.0, timestamp = clock.now))

        assertThat(manager.rawSpeed).isWithin(0.01).of(5.0)
    }

    /**
     * A missing speedAccuracy is the "no idea" case, so the deadband falls back to its fixed
     * floor — narrow enough that anything above walking pace still registers.
     */
    @Test
    fun `a fix without a speed accuracy falls back to the fixed floor`() {
        manager.process(makeFix(speed = 0.3, speedAccuracy = null, timestamp = clock.now))
        assertThat(manager.rawSpeed).isEqualTo(0.0)

        manager.process(makeFix(speed = 4.0, speedAccuracy = null, timestamp = clock.now))
        assertThat(manager.rawSpeed).isWithin(0.01).of(4.0)
    }

    /**
     * The "speed unknown" case is subsumed by the deadband — anything at or below the noise
     * floor is a standstill, whether it's noise or an admission of ignorance.
     */
    @Test
    fun `the unknown speed reads as standing still`() {
        manager.process(makeFix(speed = null, timestamp = clock.now))

        assertThat(manager.rawSpeed).isEqualTo(0.0)
    }

    /**
     * What the rider actually looks at is the smoothed value, and smoothing a standstill's
     * noise only spreads it out — the needle has to come to rest at zero and stay there.
     */
    @Test
    fun `the gauge settles at zero when the rider stops`() {
        manager.process(makeFix(speed = 8.0, timestamp = clock.now))
        check(manager.displaySpeed > 0)

        repeat(20) {
            manager.process(makeFix(speed = 0.45, speedAccuracy = 0.8, timestamp = clock.now))
        }

        assertWithMessage("the needle must rest at zero, not hover")
            .that(manager.displaySpeed).isWithin(0.01).of(0.0)
    }

    // endregion

    // region Course

    /**
     * GPS course is noise at a standstill, so below ~3.6 km/h it holds its last valid value
     * instead of spinning the direction arrow.
     */
    @Test
    fun `course is held rather than updated below the speed threshold`() {
        manager.process(makeFix(course = 90.0, speed = 8.0, timestamp = clock.now))
        check(manager.course == 90.0)

        manager.process(makeFix(course = 200.0, speed = 0.4, timestamp = clock.now))

        assertThat(manager.course).isEqualTo(90.0)
    }

    @Test
    fun `an unreliable course is ignored`() {
        manager.process(makeFix(course = 90.0, speed = 8.0, timestamp = clock.now))

        manager.process(makeFix(course = 200.0, courseAccuracy = 120.0, speed = 8.0, timestamp = clock.now))

        assertThat(manager.course).isEqualTo(90.0)
    }

    // endregion

    // region Altitude

    /** A missing vertical accuracy is the "this altitude is meaningless" case. */
    @Test
    fun `altitude is ignored when its vertical accuracy is invalid`() {
        manager.process(makeFix(altitude = 150.0, verticalAccuracy = 5.0, timestamp = clock.now))

        manager.process(makeFix(altitude = 999.0, verticalAccuracy = null, timestamp = clock.now))

        assertThat(manager.altitude).isEqualTo(150.0)
    }

    // endregion

    // region Travel direction

    /**
     * Moving, the GPS course is the answer: it is the true direction of *travel*, and unlike
     * the compass it doesn't care which way the phone is clamped to the bars.
     */
    @Test
    fun `travel direction is the gps course while moving`() {
        manager.processHeading(trueHeading = 270.0, magneticHeading = 270.0, accuracy = 5.0)

        manager.process(makeFix(course = 90.0, speed = 8.0, timestamp = clock.now))

        assertThat(manager.travelDirection!!).isWithin(0.01).of(90.0)
        assertThat(manager.isCourseLive).isTrue()
    }

    /**
     * **The feature.** Below the course threshold GPS course is noise, so it is held at its
     * last value — which leaves the arrow pointing wherever the rider was last heading, stale
     * at every red light. The magnetometer is the only thing that can still answer, so at a
     * standstill it takes over.
     */
    @Test
    fun `travel direction hands over to the compass at a standstill`() {
        manager.process(makeFix(course = 90.0, speed = 8.0, timestamp = clock.now)) // riding east
        manager.processHeading(trueHeading = 270.0, magneticHeading = 270.0, accuracy = 5.0) // phone says west

        // Stopped at the lights. Speed inside the standstill deadband, so rawSpeed is zeroed.
        manager.process(makeFix(course = 90.0, speed = 0.1, timestamp = clock.now))

        assertThat(manager.isCourseLive).isFalse()
        assertWithMessage("course itself is still sticky — that's its job")
            .that(manager.course!!).isWithin(0.01).of(90.0)
        assertWithMessage("but the direction shown is now the compass")
            .that(manager.travelDirection!!).isWithin(1.0).of(270.0)
    }

    /**
     * An emulator has no magnetometer, so no heading ever arrives. travelDirection then has
     * to degrade to exactly the behaviour it replaced — the last known course — rather than
     * going null and blanking the direction cell the moment the rider stops.
     */
    @Test
    fun `travel direction falls back to the last course when there is no compass`() {
        manager.process(makeFix(course = 90.0, speed = 8.0, timestamp = clock.now))

        manager.process(makeFix(course = 90.0, speed = 0.1, timestamp = clock.now))

        assertWithMessage("no heading was ever delivered").that(manager.heading).isNull()
        assertThat(manager.isCourseLive).isFalse()
        assertThat(manager.travelDirection!!).isWithin(0.01).of(90.0)
    }

    /**
     * Nothing has been measured yet, so there is nothing to point at — and the direction cell
     * shows its placeholder rather than an arrow aimed confidently at north.
     */
    @Test
    fun `travel direction is unknown before any sensor has reported`() {
        assertThat(LocationManager(now = clock).travelDirection).isNull()
    }

    /**
     * A negative heading accuracy is the "this reading is meaningless" sentinel — the
     * magnetometer being interfered with, which beside a bike frame and a phone speaker is
     * not rare.
     */
    @Test
    fun `a heading with invalid accuracy is ignored`() {
        manager.processHeading(trueHeading = 90.0, magneticHeading = 90.0, accuracy = 5.0)

        manager.processHeading(trueHeading = 200.0, magneticHeading = 200.0, accuracy = -1.0)

        assertWithMessage("the bad reading must not move the arrow")
            .that(manager.heading!!).isWithin(1.0).of(90.0)
    }

    /**
     * trueHeading is negative until a location fix exists to resolve magnetic declination —
     * which is exactly the moment the rider first opens the app, standing still, before any
     * fix has landed. Magnetic north is a few degrees off and entirely good enough to point
     * an arrow with.
     */
    @Test
    fun `magnetic heading is used until true heading is available`() {
        manager.processHeading(trueHeading = -1.0, magneticHeading = 135.0, accuracy = 5.0)

        assertThat(manager.heading!!).isWithin(1.0).of(135.0)
    }

    // endregion
}
