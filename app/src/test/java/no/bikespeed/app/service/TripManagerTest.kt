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

    /**
     * Under the floor is *deferred*, not thrown away. Measuring every step from the one
     * before it would mean riding slower than the floor per fix accrues nothing however long
     * it goes on — at 1 Hz and a 2.5 m floor that is everything under 9 km/h, i.e. the whole
     * grind up a steep hill.
     */
    @Test
    fun `steps under the jitter floor sum until they clear it`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        // 2 m/s is under the floor, so no single step is believable on its own — but ten
        // seconds of it is 20 m of real hill, and the rider must get credit for it.
        repeat(10) { harness.move(meters = 2.0, horizontalAccuracy = 5.0) }

        assertThat(harness.trip.accumulatedDistance).isWithin(1.0).of(20.0)
    }

    /**
     * The counterpart to the test above, and the reason the floor exists at all: summing must
     * not turn a parked bike's wander into distance. Auto-pause is off here because the rider
     * may switch it off, which leaves the floor as the only guard.
     */
    @Test
    fun `wander around one spot still accrues nothing`() {
        val harness = TripTestHarness(autoPauseEnabled = false)
        harness.anchor()
        harness.trip.start()

        // Fixes that step either side of one point rather than walking away from it: the
        // distance from the held anchor stays bounded by the wander and never clears the floor.
        repeat(10) {
            harness.move(meters = 1.0, speed = 0.0, horizontalAccuracy = 5.0)
            harness.move(meters = -1.0, speed = 0.0, horizontalAccuracy = 5.0)
        }

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

    // region Elevation

    /**
     * Elevation rides the GPS funnel — sampled at each accepted fix — so ascent and grade
     * inherit the same running/auto-pause gating that already guards distance. The harness
     * has no barometer unless a test asks for one, so the plain cases exercise the
     * GPS-altitude fallback.
     */
    @Test
    fun `ascent accumulates from gps altitude while riding`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0) // baseline sample
        for (step in 1..10) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0 + step * 5)
        }

        assertThat(harness.trip.totalAscent!!).isWithin(0.5).of(50.0)
        assertThat(harness.trip.totalDescent).isEqualTo(0.0)
    }

    @Test
    fun `descent is recorded alongside ascent`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0)
        for (step in 1..4) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0 + step * 5) // up 20
        }
        for (step in 1..6) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 120.0 - step * 5) // down 30
        }

        assertThat(harness.trip.totalAscent!!).isWithin(0.5).of(20.0)
        assertThat(harness.trip.totalDescent!!).isWithin(0.5).of(30.0)
    }

    /**
     * GPS altitude wobbles by metres at constant true height; the fallback's wider deadband
     * exists so that wobble never turns into a phantom climb.
     */
    @Test
    fun `gps altitude noise under the deadband adds no ascent`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        for (step in 0 until 20) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0 + if (step % 2 == 0) 0.0 else 2.0)
        }

        assertThat(harness.trip.totalAscent).isEqualTo(0.0)
        assertThat(harness.trip.totalDescent).isEqualTo(0.0)
    }

    @Test
    fun `the barometer is preferred over gps altitude when available`() {
        val harness = TripTestHarness(barometerAvailable = true)
        harness.anchor()
        harness.trip.start()

        harness.altimeter.relativeAltitude = 0.0
        harness.move(meters = 20.0, seconds = 2.0, altitude = 500.0) // GPS altitude is wild — it must be ignored
        harness.altimeter.relativeAltitude = 4.0
        harness.move(meters = 20.0, seconds = 2.0, altitude = 700.0)

        assertThat(harness.trip.totalAscent!!).isWithin(0.001).of(4.0)
    }

    @Test
    fun `elevation does not accumulate without a running trip`() {
        val harness = TripTestHarness()
        harness.anchor()

        harness.move(meters = 20.0, seconds = 2.0, altitude = 150.0)
        harness.move(meters = 20.0, seconds = 2.0, altitude = 200.0)

        assertWithMessage("no trip has sampled anything, so there is no figure at all")
            .that(harness.trip.totalAscent).isNull()
        assertThat(harness.trip.currentGrade).isNull()
    }

    @Test
    fun `elevation freezes during a manual pause`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0)
        harness.move(meters = 20.0, seconds = 2.0, altitude = 110.0)
        harness.trip.pause()

        harness.move(meters = 20.0, seconds = 2.0, altitude = 200.0)

        assertThat(harness.trip.totalAscent!!).isWithin(0.5).of(10.0)
    }

    /**
     * Barometric pressure drifts with the weather, which reads as altitude change while the
     * bike sits still. Auto-pause stops elevation sampling exactly as it stops distance, so a
     * long red light in a building weather front adds no climb.
     */
    @Test
    fun `barometric drift during an auto-pause adds no ascent`() {
        val harness = TripTestHarness(barometerAvailable = true)
        harness.anchor()
        harness.trip.start()
        harness.altimeter.relativeAltitude = 0.0
        harness.move(meters = 20.0, seconds = 2.0)
        autoPause(harness)
        check(harness.trip.isAutoPaused)

        harness.altimeter.relativeAltitude = 6.0 // pressure drift at the kerb
        harness.move(meters = 0.1, speed = 0.3) // still standing; the fix must not sample the drift

        assertThat(harness.trip.isAutoPaused).isTrue()
        assertThat(harness.trip.totalAscent).isEqualTo(0.0)
    }

    @Test
    fun `grade reads the current climb once its window fills`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 10.0, seconds = 1.0, altitude = 100.0)
        assertWithMessage("no grade before the window's worth of riding")
            .that(harness.trip.currentGrade).isNull()

        for (step in 1..5) {
            harness.move(meters = 10.0, seconds = 1.0, altitude = 100.0 + step * 0.5) // 5 %
        }

        assertThat(harness.trip.currentGrade!!).isWithin(0.005).of(0.05)
    }

    @Test
    fun `reset clears elevation and grade`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0)
        for (step in 1..5) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0 + step * 5)
        }
        harness.trip.pause()

        harness.trip.reset()

        assertThat(harness.trip.totalAscent).isNull()
        assertThat(harness.trip.totalDescent).isNull()
        assertThat(harness.trip.currentGrade).isNull()
    }

    @Test
    fun `the log entry carries ascent and descent`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0)
        for (step in 1..4) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0 + step * 5)
        }
        harness.trip.pause()

        val entry = harness.trip.makeLogEntry()!!

        assertThat(entry.totalAscent!!).isWithin(0.5).of(20.0)
        assertThat(entry.totalDescent!!).isWithin(0.001).of(0.0)
    }

    /**
     * Null, not zero: a trip whose fixes never carried a usable altitude has no answer to
     * "how much did you climb", and writing 0 would be indistinguishable from a genuinely
     * flat ride.
     */
    @Test
    fun `a trip without altitude data saves null rather than zero`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0, verticalAccuracy = null)
        harness.trip.pause()

        val entry = harness.trip.makeLogEntry()!!

        assertThat(entry.totalAscent).isNull()
        assertThat(entry.totalDescent).isNull()
    }

    // endregion

    // region Elevation source

    /**
     * A barometer can report hardware presence without ever delivering (its permission
     * denied). The platform wrapper folds that failure into isAvailable, and the trip must
     * then ride the GPS altitude every accepted fix already carries instead of recording
     * nothing forever.
     */
    @Test
    fun `a failed barometer falls back to gps altitude`() {
        val harness = TripTestHarness(barometerAvailable = true)
        harness.anchor()
        harness.trip.start()

        harness.altimeter.isAvailable = false // what the wrapper reports once updates error

        harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0)
        for (step in 1..4) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0 + step * 5)
        }

        assertThat(harness.trip.totalAscent!!).isWithin(0.5).of(20.0)
    }

    /**
     * The barometer needs a moment to deliver its first reading after starting. The trip
     * waits a short grace rather than latching GPS on the very first fix — but a barometer
     * that stays silent must not starve the trip past it.
     */
    @Test
    fun `a silent barometer yields to gps altitude after a grace`() {
        val harness = TripTestHarness(barometerAvailable = true)
        harness.anchor()
        harness.trip.start()

        // isAvailable stays true and relativeAltitude stays null: the pathological silent barometer.
        for (step in 0 until 5) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0 + step * 5)
        }
        assertWithMessage("still inside the grace, waiting on the barometer")
            .that(harness.trip.totalAscent).isNull()

        for (step in 5 until 14) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0 + step * 5)
        }

        val ascent = harness.trip.totalAscent
        assertWithMessage("past the grace, GPS altitude must be flowing").that(ascent).isNotNull()
        assertThat(ascent!!).isGreaterThan(0.0)
    }

    /**
     * Barometric and GPS altitude measure against different zero points, so switching source
     * mid-trip would bank the difference between them as a phantom climb. Once latched, a
     * trip stays on its source: a barometer that dies mid-ride stops the figures rather than
     * corrupting them.
     */
    @Test
    fun `the elevation source stays latched for the whole trip`() {
        val harness = TripTestHarness(barometerAvailable = true)
        harness.anchor()
        harness.trip.start()
        harness.altimeter.relativeAltitude = 0.0
        harness.move(meters = 20.0, seconds = 2.0)
        harness.altimeter.relativeAltitude = 4.0
        harness.move(meters = 20.0, seconds = 2.0)
        assertThat(harness.trip.totalAscent!!).isWithin(0.001).of(4.0)

        harness.altimeter.isAvailable = false // barometer dies mid-trip
        harness.altimeter.relativeAltitude = null
        for (step in 1..4) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 300.0 + step * 10) // GPS climbing hard
        }

        assertWithMessage("GPS altitude must not be spliced onto a barometer baseline")
            .that(harness.trip.totalAscent!!).isWithin(0.001).of(4.0)
    }

    // endregion

    // region Elevation across pauses

    /**
     * Barometric pressure drifts with the weather while the bike sits parked. Gating the
     * sampling is only half the protection: the accumulator's reference point must not
     * survive the pause either, or the whole drift lands as one phantom climb on the first
     * fix after resuming.
     */
    @Test
    fun `pause drift is not banked on resume`() {
        val harness = TripTestHarness(barometerAvailable = true)
        harness.anchor()
        harness.trip.start()
        harness.altimeter.relativeAltitude = 0.0
        harness.move(meters = 20.0, seconds = 2.0)
        harness.altimeter.relativeAltitude = 2.0
        harness.move(meters = 20.0, seconds = 2.0)
        assertThat(harness.trip.totalAscent!!).isWithin(0.001).of(2.0)

        harness.trip.pause()
        harness.clock.advance(1800.0) // half an hour at the café while a weather front moves through
        harness.trip.resume()
        harness.altimeter.relativeAltitude = 8.0 // the restarted barometer reads the drift

        harness.move(meters = 20.0, seconds = 2.0)
        harness.move(meters = 20.0, seconds = 2.0)
        assertWithMessage("the drift across the pause must anchor fresh, not count as climb")
            .that(harness.trip.totalAscent!!).isWithin(0.001).of(2.0)

        harness.altimeter.relativeAltitude = 11.0 // then a real climb after the stop
        harness.move(meters = 20.0, seconds = 2.0)
        assertThat(harness.trip.totalAscent!!).isWithin(0.001).of(5.0)
    }

    /**
     * The same protection for an auto-pause: fixes keep arriving throughout, so the resume is
     * just another accepted fix — the gap in *sampling* is what must trigger the re-baseline.
     */
    @Test
    fun `auto-pause drift is not banked on resume`() {
        val harness = TripTestHarness(barometerAvailable = true)
        harness.anchor()
        harness.trip.start()
        harness.altimeter.relativeAltitude = 0.0
        harness.move(meters = 20.0, seconds = 2.0)
        harness.move(meters = 20.0, seconds = 2.0)
        autoPause(harness)
        check(harness.trip.isAutoPaused)

        repeat(5) {
            harness.move(meters = 0.1, seconds = 5.0, speed = 0.3) // 25 s standing at the light
        }
        harness.altimeter.relativeAltitude = 6.0 // pressure drift at the kerb

        harness.move(meters = 5.0, seconds = 1.0) // pulling away releases the auto-pause
        check(!harness.trip.isAutoPaused)
        harness.move(meters = 20.0, seconds = 2.0)
        assertWithMessage("the drift accumulated while paused must not be banked")
            .that(harness.trip.totalAscent).isEqualTo(0.0)

        harness.altimeter.relativeAltitude = 8.0
        harness.move(meters = 20.0, seconds = 2.0)
        assertThat(harness.trip.totalAscent!!).isWithin(0.001).of(2.0)
    }

    /**
     * The grade window must not span a pause either: rise measured across a stop is not a
     * slope the rider is on, so the window starts empty and reads null until a fresh 30 m has
     * been ridden.
     */
    @Test
    fun `the grade window does not span a pause`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        for (step in 0 until 6) {
            harness.move(meters = 10.0, seconds = 1.0, altitude = 100.0 + step * 0.5) // 5 %
        }
        check(harness.trip.currentGrade != null)

        harness.trip.pause()
        harness.clock.advance(600.0)
        harness.trip.resume()
        harness.move(meters = 10.0, seconds = 1.0, altitude = 110.0)

        assertWithMessage("a slope measured across the pause would be a fiction — the window must refill first")
            .that(harness.trip.currentGrade).isNull()
    }

    /**
     * The gradient caption claims a live reading, so it must not keep asserting the last hill
     * while the bike stands still — unlike course, which is deliberately sticky, a grade is a
     * statement about *now*.
     */
    @Test
    fun `the grade readout clears when the trip stops being ridden`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        for (step in 0 until 6) {
            harness.move(meters = 10.0, seconds = 1.0, altitude = 100.0 + step * 0.5)
        }
        check(harness.trip.currentGrade != null)

        autoPause(harness)
        check(harness.trip.isAutoPaused)
        assertWithMessage("auto-paused at the crest, the panel must not still read a climb")
            .that(harness.trip.currentGrade).isNull()

        harness.move(meters = 5.0, seconds = 1.0) // resume
        for (step in 0 until 6) {
            harness.move(meters = 10.0, seconds = 1.0, altitude = 103.0 + step * 0.5)
        }
        check(harness.trip.currentGrade != null) { "riding again refills the window" }

        harness.trip.pause()
        assertThat(harness.trip.currentGrade).isNull()
    }

    // endregion

    // region Elevation at a standstill

    /**
     * With auto-pause switched off, nothing else stops fixes from flowing at a standstill.
     * Distance is protected by the jitter floor; elevation must be gated the same way, or GPS
     * altitude wandering at a red light accrues as climb while the bike never moves.
     */
    @Test
    fun `a standstill with auto-pause disabled adds no ascent`() {
        val harness = TripTestHarness(autoPauseEnabled = false)
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0)
        harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0)

        for (step in 0 until 30) { // a long light, GPS altitude wandering ±4.5 m beyond the 3 m deadband
            harness.move(
                meters = 0.1, seconds = 1.0, speed = 0.0,
                altitude = 100.0 + if (step % 2 == 0) 4.5 else -4.5,
            )
        }

        assertThat(harness.trip.totalAscent).isEqualTo(0.0)
        assertThat(harness.trip.totalDescent).isEqualTo(0.0)
    }

    /**
     * GPS vertical accuracy of 20–30 m against a 3 m deadband would manufacture hundreds of
     * phantom metres in an urban canyon. A fix whose altitude error bar dwarfs the deadband
     * contributes nothing.
     */
    @Test
    fun `poor vertical accuracy does not feed the climb`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        for (step in 0 until 5) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 100.0 + step * 5, verticalAccuracy = 40.0)
        }
        assertWithMessage("an error bar wider than the climb is not data")
            .that(harness.trip.totalAscent).isNull()

        harness.move(meters = 20.0, seconds = 2.0, altitude = 130.0, verticalAccuracy = 5.0)
        for (step in 1..4) {
            harness.move(meters = 20.0, seconds = 2.0, altitude = 130.0 + step * 5, verticalAccuracy = 5.0)
        }
        assertThat(harness.trip.totalAscent!!).isWithin(0.5).of(20.0)
    }

    // endregion

    // region Barometer lifecycle

    /**
     * The barometer runs for exactly the span of a recording, like background location
     * updates: the sensor isn't held hot while the app sits idle.
     */
    @Test
    fun `barometer updates follow the trip lifecycle`() {
        val harness = TripTestHarness(barometerAvailable = true)
        harness.anchor()
        assertThat(harness.altimeter.isUpdating).isFalse()

        harness.trip.start()
        assertThat(harness.altimeter.isUpdating).isTrue()

        harness.trip.pause()
        assertThat(harness.altimeter.isUpdating).isFalse()

        harness.trip.resume()
        assertThat(harness.altimeter.isUpdating).isTrue()

        harness.trip.pause()
        harness.trip.reset()
        assertThat(harness.altimeter.isUpdating).isFalse()
    }

    // endregion

    // region Saving

    @Test
    fun `a running trip cannot be saved`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0)

        assertThat(harness.trip.canSaveTrip).isFalse()
        assertThat(harness.trip.makeLogEntry()).isNull()
    }

    /**
     * An auto-pause is a flag on RUNNING, not a state case, precisely so the Save button does
     * not light up at every red light.
     */
    @Test
    fun `an auto-paused trip cannot be saved`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0)
        autoPause(harness)

        assertThat(harness.trip.isAutoPaused).isTrue()
        assertThat(harness.trip.canSaveTrip).isFalse()
    }

    @Test
    fun `a trip too small to be meaningful cannot be saved`() {
        val cases = listOf(
            5.0 to 10.0, // far enough? no — under the 10 m floor
            100.0 to 4.0, // long enough? no — under the 5 s floor
        )
        for ((distance, seconds) in cases) {
            val harness = TripTestHarness()
            harness.anchor()
            harness.trip.start()
            harness.move(meters = distance, seconds = seconds)
            harness.trip.pause()

            assertThat(harness.trip.canSaveTrip).isFalse()
            assertThat(harness.trip.makeLogEntry()).isNull()
        }
    }

    @Test
    fun `a paused trip of substance can be saved`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0)
        harness.trip.pause()

        assertThat(harness.trip.canSaveTrip).isTrue()

        val entry = harness.trip.makeLogEntry()!!
        assertThat(entry.distance).isWithin(0.5).of(100.0)
        assertThat(entry.duration).isWithin(0.01).of(10.0)
        assertThat(entry.averageSpeed).isWithin(0.1).of(10.0)
    }

    // endregion

    // region Route capture

    /**
     * The track hangs off consume() like everything else, so it inherits the trip-state
     * gating — a phone sitting on a table before the ride must not record a track.
     */
    @Test
    fun `no route is recorded before the trip starts`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.move(meters = 8.0)
        harness.move(meters = 8.0)

        assertThat(harness.trip.routeSamples).isEmpty()
    }

    /**
     * The rider sets off from where they set off — not from 10 m down the road. A trip
     * started while the app was already tracking has an anchor from before start(), so its
     * first *running* fix has to be captured even though no distance has accrued against the
     * sampling interval yet.
     */
    @Test
    fun `the track begins at the first fix after start`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 5.0)

        assertThat(harness.trip.routeSamples).hasSize(1)
        val start = harness.trip.routeSamples.first()
        assertThat(start.latitude)
            .isWithin(0.00001)
            .of(no.bikespeed.app.fixtures.Fix.north(no.bikespeed.app.fixtures.Fix.OSLO_LATITUDE, 5.0))
    }

    /**
     * Fixes arrive about once a second; a track that kept every one of them would be a few
     * thousand points for an hour's ride, most of them a metre apart. Decimating by distance
     * keeps the shape and drops the rest — 100 m at a 10 m interval is ~10 points, not the 20
     * fixes that produced them.
     */
    @Test
    fun `the track is decimated by distance rather than keeping every fix`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        repeat(20) {
            harness.move(meters = 5.0) // 20 fixes, 100 m
        }

        assertThat(harness.trip.accumulatedDistance).isWithin(0.5).of(100.0)
        assertWithMessage("expected ~10 samples, got ${harness.trip.routeSamples.size}")
            .that(harness.trip.routeSamples.size).isIn(9..12)
    }

    @Test
    fun `route samples carry the altitude and time of their fix`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 5.0, speed = 6.0, altitude = 137.0)

        val sample = harness.trip.routeSamples.first()
        assertThat(sample.altitude!!).isWithin(0.01).of(137.0)
        assertThat(sample.timestamp).isEqualTo(harness.clock.now)
        // The live GPS speed rides along on the point, so the trip-log speed profile can
        // prefer it over deriving speed from positions.
        assertThat(sample.speed!!).isWithin(0.01).of(6.0)
        // And the accumulated distance, so the speed profile shares the height profile's
        // X-axis exactly instead of re-deriving a noisier one from raw coordinates.
        assertThat(sample.distance!!).isWithin(0.5).of(5.0)
    }

    /**
     * The platform reports no speed when it can't measure one (e.g. at a standstill). That
     * must not be stored as a real reading — the point keeps `speed == null` and the speed
     * profile derives a value for it instead.
     */
    @Test
    fun `a route sample drops the unknown speed`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 5.0, speed = null) // the "unknown speed" case

        val sample = harness.trip.routeSamples.first()
        assertThat(sample.speed).isNull()
    }

    /**
     * GPX has no way to say "the height here is unknown", so a fix whose vertical accuracy is
     * unusable contributes a point with no altitude rather than one claiming sea level. The
     * point itself is still worth having: the rider was there, and the map only needs the
     * coordinate.
     */
    @Test
    fun `a fix without usable altitude still records its position`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()

        harness.move(meters = 5.0, verticalAccuracy = null) // the "no altitude" case

        val sample = harness.trip.routeSamples.first()
        assertThat(sample.altitude).isNull()
        assertThat(sample.longitude).isWithin(0.00001).of(no.bikespeed.app.fixtures.Fix.OSLO_LONGITUDE)
    }

    /**
     * Standing at a red light for two minutes must not bank 120 identical points — and it
     * doesn't, because an auto-paused trip stops at the same guard that stops distance.
     */
    @Test
    fun `an auto-paused trip records no further track`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 20.0, seconds = 2.0)
        autoPause(harness)
        val atPause = harness.trip.routeSamples.size

        repeat(10) {
            harness.move(meters = 0.1, speed = 0.3)
        }

        assertThat(harness.trip.routeSamples).hasSize(atPause)
    }

    @Test
    fun `reset clears the track`() {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        repeat(10) {
            harness.move(meters = 5.0)
        }
        harness.trip.pause()

        harness.trip.reset()

        assertThat(harness.trip.routeSamples).isEmpty()
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
