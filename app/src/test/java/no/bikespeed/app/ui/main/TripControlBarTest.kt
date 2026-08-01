package no.bikespeed.app.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import no.bikespeed.app.fixtures.TripTestHarness
import no.bikespeed.app.model.TripState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The control bar against a real TripManager (driven by the test harness) and an injectable
 * save function — enablement across the state machine, and the load-bearing save ordering:
 * reset only after the write lands, the trip retained on failure.
 */
@RunWith(RobolectricTestRunner::class)
class TripControlBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `save and reset are disabled until a substantial trip is paused`() {
        val harness = TripTestHarness()
        compose.setContent {
            TripControlBar(tripManager = harness.trip, save = { _, _, _ -> })
        }

        compose.onNodeWithTag("saveButton").assertIsNotEnabled()
        compose.onNodeWithTag("resetButton").assertIsNotEnabled()

        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0)
        compose.waitForIdle()
        compose.onNodeWithTag("saveButton").assertIsNotEnabled() // still running

        harness.trip.pause()
        compose.waitForIdle()
        compose.onNodeWithTag("saveButton").assertIsEnabled()
        compose.onNodeWithTag("resetButton").assertIsEnabled()
    }

    @Test
    fun `a successful save resets the trip and confirms`() {
        val harness = TripTestHarness()
        var saved = 0
        compose.setContent {
            TripControlBar(tripManager = harness.trip, save = { _, _, _ -> saved++ })
        }
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0)
        harness.trip.pause()
        compose.waitForIdle()

        compose.onNodeWithTag("saveButton").performClick()
        compose.waitForIdle()

        assertThat(saved).isEqualTo(1)
        assertThat(harness.trip.state).isEqualTo(TripState.IDLE)
        assertThat(harness.trip.accumulatedDistance).isEqualTo(0.0)
        compose.onNodeWithTag("savedDialog").assertIsDisplayed()
    }

    /**
     * **The load-bearing ordering.** reset() throws away the only other copy of the ride, so
     * it must not run until the store says the trip is on disk. On failure the trip stays
     * paused and still theirs; tapping Save again retries.
     */
    @Test
    fun `a failed save keeps the trip so the rider can retry`() {
        val harness = TripTestHarness()
        compose.setContent {
            TripControlBar(
                tripManager = harness.trip,
                save = { _, _, _ -> throw RuntimeException("disk full") },
            )
        }
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0)
        harness.trip.pause()
        compose.waitForIdle()

        compose.onNodeWithTag("saveButton").performClick()
        compose.waitForIdle()

        assertThat(harness.trip.state).isEqualTo(TripState.PAUSED)
        assertThat(harness.trip.accumulatedDistance).isGreaterThan(0.0)
        assertThat(harness.trip.canSaveTrip).isTrue()
        compose.onNodeWithTag("saveFailureDialog").assertIsDisplayed()
    }

    /**
     * Save must be single-flight: canSaveTrip stays true until the write lands and reset()
     * runs, and makeLogEntry() mints a fresh UUID per call — so without a guard, a
     * double-tap (easy on a handlebar mount) writes the same ride twice under different
     * primary keys and inflates every lifetime aggregate.
     */
    @Test
    fun `a second tap while the save is in flight does not save twice`() {
        val harness = TripTestHarness()
        val gate = CompletableDeferred<Unit>()
        var saved = 0
        compose.setContent {
            TripControlBar(
                tripManager = harness.trip,
                save = { _, _, _ ->
                    saved++
                    gate.await()
                },
            )
        }
        harness.anchor()
        harness.trip.start()
        harness.move(meters = 100.0, seconds = 10.0)
        harness.trip.pause()
        compose.waitForIdle()

        compose.onNodeWithTag("saveButton").performClick()
        compose.onNodeWithTag("saveButton").performClick() // rider's double-tap
        gate.complete(Unit)
        compose.waitForIdle()

        assertThat(saved).isEqualTo(1)
        assertThat(harness.trip.state).isEqualTo(TripState.IDLE)
        compose.onNodeWithTag("savedDialog").assertIsDisplayed()
    }

    @Test
    fun `the primary button walks the state machine`() {
        val harness = TripTestHarness()
        compose.setContent {
            TripControlBar(tripManager = harness.trip, save = { _, _, _ -> })
        }

        compose.onNodeWithTag("primaryButton").performClick() // Start
        compose.waitForIdle()
        assertThat(harness.trip.state).isEqualTo(TripState.RUNNING)

        compose.onNodeWithTag("primaryButton").performClick() // Pause
        compose.waitForIdle()
        assertThat(harness.trip.state).isEqualTo(TripState.PAUSED)

        compose.onNodeWithTag("primaryButton").performClick() // Resume
        compose.waitForIdle()
        assertThat(harness.trip.state).isEqualTo(TripState.RUNNING)
    }
}
