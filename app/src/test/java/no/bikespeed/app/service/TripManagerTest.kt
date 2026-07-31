package no.bikespeed.app.service

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import no.bikespeed.app.fixtures.TripTestHarness
import no.bikespeed.app.model.TripState
import org.junit.Test

/**
 * Pins the behaviour of the trip accumulator, ported case-for-case from the iOS suite —
 * everything here took a season of riding to get right, and the port must not re-learn it.
 */
class TripManagerTest {

    // region State machine

    @Test
    fun `trip starts idle`() {
        val harness = TripTestHarness()
        assertThat(harness.trip.state).isEqualTo(TripState.IDLE)
        assertThat(harness.trip.accumulatedDistance).isEqualTo(0.0)
    }

    @Test
    fun `start pause resume moves through the states`() {
        val harness = TripTestHarness()

        harness.trip.start()
        assertThat(harness.trip.state).isEqualTo(TripState.RUNNING)

        harness.trip.pause()
        assertThat(harness.trip.state).isEqualTo(TripState.PAUSED)

        harness.trip.resume()
        assertThat(harness.trip.state).isEqualTo(TripState.RUNNING)
    }

    @Test
    fun `reset returns everything to zero`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 50.0, seconds = 5.0)
        harness.trip.pause()

        harness.trip.reset()

        assertThat(harness.trip.state).isEqualTo(TripState.IDLE)
        assertThat(harness.trip.accumulatedDistance).isEqualTo(0.0)
        assertThat(harness.trip.elapsedActiveDuration).isEqualTo(0.0)
        assertThat(harness.trip.maxSpeed).isEqualTo(0.0)
    }

    /** Reset is only offered from idle or paused; a running trip must not be wiped out from under the rider. */
    @Test
    fun `reset does nothing while running`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 50.0, seconds = 5.0)

        harness.trip.reset()

        assertThat(harness.trip.state).isEqualTo(TripState.RUNNING)
        assertThat(harness.trip.accumulatedDistance).isWithin(0.5).of(50.0)
    }

    // endregion

    // region Reset availability

    @Test
    fun `a trip that was never started has nothing to reset`() {
        val harness = TripTestHarness()
        assertThat(harness.trip.canResetTrip).isFalse()
    }

    /**
     * The bug this pins: after Reset the trip is back at zero, so the button had nothing left
     * to do — yet it stayed enabled, inviting a tap that visibly changes nothing.
     */
    @Test
    fun `resetting a trip leaves nothing left to reset`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 50.0, seconds = 5.0)
        harness.trip.pause()

        assertThat(harness.trip.canResetTrip).isTrue()

        harness.trip.reset()

        assertThat(harness.trip.canResetTrip).isFalse()
    }

    /**
     * Same rule the reset() guard already enforces: a running trip must not be wiped out from
     * under the rider — pause it first.
     */
    @Test
    fun `a running trip cannot be reset`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 50.0, seconds = 5.0)

        assertThat(harness.trip.canResetTrip).isFalse()
    }

    /**
     * Reset stays available for a trip too short to save — clearing a false start is exactly
     * what it's for, and it must not inherit canSaveTrip's distance and duration floors.
     */
    @Test
    fun `a paused trip too small to save can still be reset`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 5.0, seconds = 2.0)
        harness.trip.pause()

        assertThat(harness.trip.canSaveTrip).isFalse()
        assertThat(harness.trip.canResetTrip).isTrue()
    }

    // endregion

    // region Distance

    @Test
    fun `distance accumulates across fixes while running`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 50.0, seconds = 5.0)
        harness.move(meters = 50.0, seconds = 5.0)

        assertThat(harness.trip.accumulatedDistance).isWithin(0.5).of(100.0)
    }

    /**
     * Fixes keep arriving when no trip is running — the gauge and compass stay live — but they
     * must not quietly build up a distance the rider never asked to record.
     */
    @Test
    fun `distance does not accumulate while idle`() {
        val harness = TripTestHarness()
        harness.anchor()

        harness.move(meters = 50.0, seconds = 5.0)
        harness.move(meters = 50.0, seconds = 5.0)

        assertThat(harness.trip.accumulatedDistance).isEqualTo(0.0)
    }

    @Test
    fun `distance does not accumulate while paused`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 50.0, seconds = 5.0)

        harness.trip.pause()
        harness.move(meters = 50.0, seconds = 5.0)
        harness.move(meters = 50.0, seconds = 5.0)

        assertThat(harness.trip.accumulatedDistance).isWithin(0.5).of(50.0)
    }

    /**
     * Resuming must not bank the ground covered while paused: the anchor is kept fresh through
     * the pause, so the first fix after Resume measures from where the rider actually is.
     */
    @Test
    fun `resuming does not bank the distance covered while paused`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 50.0, seconds = 5.0)

        harness.trip.pause()
        harness.move(meters = 1_000.0, seconds = 60.0) // wheeled the bike somewhere, or the signal wandered
        harness.trip.resume()

        harness.move(meters = 50.0, seconds = 5.0)

        assertThat(harness.trip.accumulatedDistance).isWithin(0.5).of(100.0)
    }

    // endregion

    // region Jitter floor

    /**
     * A stationary phone's fix wanders by a metre or two. Movement under
     * `max(1 m, 0.5 × accuracy)` is treated as that wander and contributes nothing, or a bike
     * left at a café would ride a few km.
     */
    @Test
    fun `movement under the jitter floor is ignored`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        // Floor is max(1, 0.5 × 5) = 2.5 m.
        harness.move(meters = 2.0, horizontalAccuracy = 5.0)

        assertThat(harness.trip.accumulatedDistance).isEqualTo(0.0)
    }

    @Test
    fun `movement above the jitter floor counts`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 5.0, horizontalAccuracy = 5.0)

        assertThat(harness.trip.accumulatedDistance).isWithin(0.5).of(5.0)
    }

    /** The floor scales with accuracy, so a sloppy fix has to move further before it is believed. */
    @Test
    fun `the jitter floor scales with horizontal accuracy`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        // Floor is max(1, 0.5 × 20) = 10 m, so an 8 m step is noise at this accuracy even
        // though it would have counted at a tighter one.
        harness.move(meters = 8.0, horizontalAccuracy = 20.0)

        assertThat(harness.trip.accumulatedDistance).isEqualTo(0.0)
    }

    // endregion

    // region Teleport guard

    @Test
    fun `a teleport fix is rejected`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        // 1 km in one second is 3600 km/h. Not a bike.
        harness.move(meters = 1_000.0, seconds = 1.0)

        assertThat(harness.trip.accumulatedDistance).isEqualTo(0.0)
    }

    /**
     * The guard cannot tell which of the two fixes is the artifact, so it assumes the new one
     * is — right for an isolated spike. But if the *anchor* is the bad fix, every later fix
     * measures implausibly against it and distance freezes for the rest of the ride. A run of
     * rejections means the anchor is what's wrong: re-anchor and write off the gap rather
     * than the trip.
     */
    @Test
    fun `distance recovers after a streak of rejections`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 1_000.0)
        harness.move(meters = 1_000.0)
        harness.move(meters = 1_000.0) // third rejection: the anchor gives up and moves here

        assertWithMessage("the implausible fixes themselves must not count")
            .that(harness.trip.accumulatedDistance).isEqualTo(0.0)

        harness.move(meters = 20.0)

        assertWithMessage("riding must resume counting from the new anchor")
            .that(harness.trip.accumulatedDistance).isWithin(0.5).of(20.0)
    }

    // endregion

    // region Max speed

    @Test
    fun `max speed tracks the peak not the latest`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 5.0, speed = 5.0)
        harness.move(meters = 12.0, speed = 12.0)
        harness.move(meters = 8.0, speed = 8.0)

        assertThat(harness.trip.maxSpeed).isWithin(0.01).of(12.0)
    }

    /**
     * A GPS spike can report an absurd instantaneous speed on an otherwise sane fix. Max
     * speed is clamped at the same ceiling distance uses, so one bad sample can't leave a
     * 700 km/h "record" in the trip log.
     */
    @Test
    fun `max speed is clamped at the plausible ceiling`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 5.0, speed = 200.0)

        assertThat(harness.trip.maxSpeed).isWithin(0.01).of(120.0 / 3.6)
    }

    /** The platform reports "I don't know" as an absent speed, not a slow one. */
    @Test
    fun `an unknown speed does not disturb max speed`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 10.0, speed = 10.0)
        harness.move(meters = 5.0, speed = null)

        assertThat(harness.trip.maxSpeed).isWithin(0.01).of(10.0)
    }

    // endregion

    // region Time and average speed

    @Test
    fun `elapsed time counts only while running`() {
        val harness = TripTestHarness()
        harness.trip.start()

        harness.clock.advance(10.0)
        harness.trip.tick()
        assertThat(harness.trip.elapsedActiveDuration).isWithin(0.01).of(10.0)

        harness.trip.pause()
        harness.clock.advance(60.0) // sitting in a café
        harness.trip.tick()
        assertThat(harness.trip.elapsedActiveDuration).isWithin(0.01).of(10.0)

        harness.trip.resume()
        harness.clock.advance(5.0)
        harness.trip.tick()
        assertThat(harness.trip.elapsedActiveDuration).isWithin(0.01).of(15.0)
    }

    @Test
    fun `average speed is distance over active time`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 100.0, seconds = 10.0)

        assertThat(harness.trip.averageSpeed).isWithin(0.1).of(10.0)
    }

    // endregion

    // region Auto-pause

    /**
     * GPS speed drifts around a standstill, so the pause has to wait out a debounce rather
     * than firing on the first slow fix.
     */
    @Test
    fun `auto-pause waits out the debounce`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 0.1, speed = 0.3)
        harness.move(meters = 0.1, speed = 0.3)
        assertWithMessage("under 3 s below the threshold is not a standstill yet")
            .that(harness.trip.isAutoPaused).isFalse()

        harness.move(meters = 0.1, speed = 0.3)
        harness.move(meters = 0.1, speed = 0.3)
        assertThat(harness.trip.isAutoPaused).isTrue()
    }

    /**
     * The resume threshold sits above the pause threshold on purpose. A single cutoff would
     * flap on and off as the standstill drift crosses it.
     */
    @Test
    fun `auto-pause holds through speeds between the two thresholds`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        autoPause(harness)

        harness.move(meters = 0.7, speed = 0.7) // 2.5 km/h: above the pause threshold, below the resume one
        assertWithMessage("hysteresis: this must not be read as riding again")
            .that(harness.trip.isAutoPaused).isTrue()

        harness.move(meters = 1.0, speed = 1.0) // 3.6 km/h: genuinely moving
        assertThat(harness.trip.isAutoPaused).isFalse()
    }

    /**
     * Freezing the clock is the whole point: average speed is distance ÷ active time, so
     * counting time at every red light would drag the average down all ride.
     */
    @Test
    fun `auto-pause freezes both distance and the clock`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0)
        autoPause(harness)

        val distanceAtPause = harness.trip.accumulatedDistance
        val elapsedAtPause = harness.trip.elapsedActiveDuration

        harness.clock.advance(120.0)
        harness.trip.tick()

        assertThat(harness.trip.accumulatedDistance).isWithin(0.5).of(distanceAtPause)
        assertThat(harness.trip.elapsedActiveDuration).isWithin(0.01).of(elapsedAtPause)
    }

    /**
     * An auto-pause is only ever entered on positive evidence — a fix reporting a standstill
     * — so it has to be released once that evidence stops arriving. Accuracy often degrades
     * *because* the phone is still, and a rider who pulled away through a poor-signal stretch
     * would otherwise have that riding time silently vanish from the trip.
     */
    @Test
    fun `auto-pause is released when fixes stop arriving to justify it`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        autoPause(harness)

        harness.clock.advance(6.0) // no usable fix for longer than the watchdog allows
        harness.trip.tick()

        assertThat(harness.trip.isAutoPaused).isFalse()
        assertThat(harness.trip.state).isEqualTo(TripState.RUNNING)
    }

    /**
     * A manual pause supersedes an auto-pause. Rolling forward must never resume a trip the
     * rider deliberately stopped.
     */
    @Test
    fun `moving again does not resume a manually paused trip`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.trip.pause()

        harness.move(meters = 20.0, speed = 8.0)

        assertThat(harness.trip.state).isEqualTo(TripState.PAUSED)
        assertThat(harness.trip.accumulatedDistance).isEqualTo(0.0)
    }

    @Test
    fun `auto-pause never engages when the setting is off`() {
        val harness = TripTestHarness(autoPauseEnabled = false)
        harness.anchor()
        harness.trip.start()

        autoPause(harness)

        assertThat(harness.trip.isAutoPaused).isFalse()
    }

    @Test
    fun `turning the setting off releases an active auto-pause`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        autoPause(harness)
        check(harness.trip.isAutoPaused)

        harness.settings.autoPauseEnabled = false
        // The release happens on the trip clock rather than instantly off a subscription,
        // because an auto-paused rider is standing still and there may be no next fix to
        // notice the toggle at. The rider sees it within 0.5 s; the test drives the same
        // tick directly rather than waiting for it.
        harness.trip.tick()

        assertThat(harness.trip.isAutoPaused).isFalse()
    }

    /**
     * An auto-pause is a flag on RUNNING, so it must not unlock Reset at a red light any more
     * than it unlocks Save.
     */
    @Test
    fun `an auto-paused trip cannot be reset`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0)
        autoPause(harness)

        assertThat(harness.trip.isAutoPaused).isTrue()
        assertThat(harness.trip.canResetTrip).isFalse()
    }

    /**
     * An auto-pause is still a recording: the only way out of it is a fix above the resume
     * threshold, and with the phone locked at a red light that fix can only arrive if GPS
     * stays on. Releasing background updates here would freeze the trip at the first light
     * after the screen locks.
     */
    @Test
    fun `an auto-pause does not release background updates`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        autoPause(harness)

        assertThat(harness.trip.isAutoPaused).isTrue()
        assertThat(harness.locationSource.backgroundUpdatesEnabled).isTrue()
    }

    // endregion

    // region Background recording

    /**
     * GPS in the background costs battery, so it's held exactly for the span of a recording:
     * on while the trip is running, off the moment it isn't. Asserted against the fake's
     * recorded state — the behaviour is "TripManager asked for it", never how the platform
     * honours the ask.
     */
    @Test
    fun `background updates are held exactly while the trip records`() {
        val harness = TripTestHarness()
        assertWithMessage("merely constructing a trip must not hold GPS awake")
            .that(harness.locationSource.backgroundUpdatesEnabled).isFalse()

        harness.trip.start()
        assertThat(harness.locationSource.backgroundUpdatesEnabled).isTrue()

        harness.trip.pause()
        assertThat(harness.locationSource.backgroundUpdatesEnabled).isFalse()

        harness.trip.resume()
        assertThat(harness.locationSource.backgroundUpdatesEnabled).isTrue()
    }

    @Test
    fun `resetting a trip releases background updates`() {
        val harness = TripTestHarness()
        harness.trip.start()
        harness.trip.pause()

        harness.trip.reset()

        assertThat(harness.locationSource.backgroundUpdatesEnabled).isFalse()
    }

    /**
     * Fixes flow whenever the app is open — the gauge is live from launch — but a trip nobody
     * started must not be the thing keeping GPS running under a locked screen.
     */
    @Test
    fun `fixes arriving without a trip do not request background updates`() {
        val harness = TripTestHarness()
        harness.anchor()

        harness.move(meters = 50.0, seconds = 5.0)

        assertThat(harness.locationSource.backgroundUpdatesEnabled).isFalse()
    }

    // endregion

    // region Helpers

    /** Four fixes below the pause threshold, spanning more than the 3 s debounce. */
    private fun autoPause(harness: TripTestHarness) {
        repeat(4) {
            harness.move(meters = 0.1, speed = 0.3)
        }
    }

    // endregion
}
