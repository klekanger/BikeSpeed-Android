package no.bikespeed.app.service

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import no.bikespeed.app.fixtures.TripTestHarness
import org.junit.Test

/**
 * The production driver of the trip clock. Everything tick() *does* is pinned by
 * TripManagerTest, which calls it directly; these pin that the ticker keeps calling it from
 * whatever scope owns it. In production that is the app scope — deliberately not a screen:
 * the auto-pause watchdog and the elapsed-time republish must survive the main screen
 * leaving composition (Settings, the trip log) and the activity being destroyed while the
 * foreground service records. The iOS analogue is TripManager's own repeating timer.
 */
class TripTickerTest {

    @Test
    fun `the ticker republishes elapsed time without anyone calling tick`() = runTest {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.trip.startTicker(backgroundScope)

        harness.clock.advance(10.0)
        testScheduler.advanceTimeBy(600) // past one 500 ms ticker interval, on virtual time

        assertThat(harness.trip.elapsedActiveDuration).isEqualTo(10.0)
    }

    @Test
    fun `the ticker keeps driving the clock across intervals`() = runTest {
        val harness = TripTestHarness()
        harness.anchor()
        harness.trip.start()
        harness.trip.startTicker(backgroundScope)

        harness.clock.advance(3.0)
        testScheduler.advanceTimeBy(600)
        assertThat(harness.trip.elapsedActiveDuration).isEqualTo(3.0)

        harness.clock.advance(4.0)
        testScheduler.advanceTimeBy(600)
        assertThat(harness.trip.elapsedActiveDuration).isEqualTo(7.0)
    }
}
