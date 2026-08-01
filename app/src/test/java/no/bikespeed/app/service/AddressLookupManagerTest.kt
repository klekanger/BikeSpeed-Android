package no.bikespeed.app.service

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.bikespeed.app.fixtures.FakeLocationSource
import no.bikespeed.app.fixtures.Fix
import no.bikespeed.app.fixtures.TestClock
import no.bikespeed.app.fixtures.makeFix
import no.bikespeed.app.model.LocationFix
import org.junit.Test

/**
 * The lookup gating exists because reverse geocoding is a network call the platform
 * throttles. The iOS suite has no dedicated tests for this manager — the behaviour spec is
 * its doc comments and constants — so these pin it directly: the 75 m / 10 s gates are
 * deliberate (BikeSpeed is used in cars too; see the street-lookup-cadence note), the
 * backoff doubles to 120 s, and a failure never drops a good answer.
 */
class AddressLookupManagerTest {

    private class FakeGeocoder : ReverseGeocoder {
        val requests = mutableListOf<LocationFix>()
        var result: Result<String?> = Result.success("Storgata")
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun streetName(fix: LocationFix): String? {
            requests.add(fix)
            gate?.await()
            return result.getOrThrow()
        }
    }

    private class Harness(scope: kotlinx.coroutines.CoroutineScope) {
        val clock = TestClock()
        val source = FakeLocationSource()
        val geocoder = FakeGeocoder()
        val manager = AddressLookupManager(
            locationSource = source,
            geocoder = geocoder,
            scope = scope,
            now = clock,
        )
        private var latitude = Fix.OSLO_LATITUDE

        /** Advance the clock and deliver a fix [meters] further north. */
        fun move(meters: Double, seconds: Double = 1.0) {
            clock.advance(seconds)
            latitude = Fix.north(latitude, meters)
            source.emit(makeFix(latitude = latitude, timestamp = clock.now))
        }
    }

    @Test
    fun `the first fix triggers a lookup and publishes the street`() = runTest {
        val harness = Harness(this)

        harness.move(meters = 0.0)
        advanceUntilIdle()

        assertThat(harness.geocoder.requests).hasSize(1)
        assertThat(harness.manager.streetName).isEqualTo("Storgata")
    }

    @Test
    fun `fixes inside the distance gate do not trigger another lookup`() = runTest {
        val harness = Harness(this)
        harness.move(meters = 0.0)
        advanceUntilIdle()

        harness.move(meters = 50.0, seconds = 60.0) // plenty of time, not enough distance
        advanceUntilIdle()

        assertWithMessage("50 m from the last resolved position is inside the 75 m gate")
            .that(harness.geocoder.requests).hasSize(1)
    }

    @Test
    fun `fixes inside the time gate do not trigger another lookup`() = runTest {
        val harness = Harness(this)
        harness.move(meters = 0.0)
        advanceUntilIdle()

        harness.move(meters = 100.0, seconds = 5.0) // plenty of distance, not enough time
        advanceUntilIdle()

        assertWithMessage("5 s since the last attempt is inside the 10 s gate")
            .that(harness.geocoder.requests).hasSize(1)
    }

    @Test
    fun `moving far enough after long enough triggers a fresh lookup`() = runTest {
        val harness = Harness(this)
        harness.move(meters = 0.0)
        advanceUntilIdle()

        harness.geocoder.result = Result.success("Karl Johans gate")
        harness.move(meters = 100.0, seconds = 15.0)
        advanceUntilIdle()

        assertThat(harness.geocoder.requests).hasSize(2)
        assertThat(harness.manager.streetName).isEqualTo("Karl Johans gate")
    }

    /** A network blip is no reason to drop a good answer. */
    @Test
    fun `a failure keeps the last resolved street`() = runTest {
        val harness = Harness(this)
        harness.move(meters = 0.0)
        advanceUntilIdle()

        harness.geocoder.result = Result.failure(RuntimeException("network down"))
        harness.move(meters = 100.0, seconds = 15.0)
        advanceUntilIdle()

        assertThat(harness.manager.streetName).isEqualTo("Storgata")
    }

    /**
     * If we *are* throttled, retrying at a fixed 10 s only keeps us throttled — the interval
     * doubles per consecutive failure.
     */
    @Test
    fun `a failure backs the retry interval off`() = runTest {
        val harness = Harness(this)
        harness.move(meters = 0.0)
        advanceUntilIdle()

        harness.geocoder.result = Result.failure(RuntimeException("throttled"))
        harness.move(meters = 100.0, seconds = 15.0) // attempt 2 — fails
        advanceUntilIdle()
        assertThat(harness.geocoder.requests).hasSize(2)

        harness.move(meters = 100.0, seconds = 15.0) // 15 s later: inside the backed-off 20 s
        advanceUntilIdle()
        assertWithMessage("one failure doubles the interval to 20 s, so 15 s is too soon")
            .that(harness.geocoder.requests).hasSize(2)

        harness.geocoder.result = Result.success("Bogstadveien")
        harness.move(meters = 100.0, seconds = 10.0) // now 25 s since the failed attempt
        advanceUntilIdle()
        assertThat(harness.geocoder.requests).hasSize(3)
        assertThat(harness.manager.streetName).isEqualTo("Bogstadveien")
    }

    /** A success resets the backoff so the next window is the base interval again. */
    @Test
    fun `a success resets the backoff`() = runTest {
        val harness = Harness(this)
        harness.move(meters = 0.0)
        advanceUntilIdle()

        harness.geocoder.result = Result.failure(RuntimeException("throttled"))
        harness.move(meters = 100.0, seconds = 25.0) // fails, backoff now 20 s
        advanceUntilIdle()

        harness.geocoder.result = Result.success("Storgata")
        harness.move(meters = 100.0, seconds = 25.0) // succeeds, backoff resets
        advanceUntilIdle()

        harness.geocoder.result = Result.success("Bogstadveien")
        harness.move(meters = 100.0, seconds = 12.0) // 12 s > base 10 s: allowed again
        advanceUntilIdle()

        assertThat(harness.manager.streetName).isEqualTo("Bogstadveien")
    }

    /**
     * A resolved "nothing" — genuinely no street here (trail, out at sea) — blanks the
     * caption and advances both gates, unlike a failure.
     */
    @Test
    fun `a resolved null blanks the street`() = runTest {
        val harness = Harness(this)
        harness.move(meters = 0.0)
        advanceUntilIdle()
        assertThat(harness.manager.streetName).isEqualTo("Storgata")

        harness.geocoder.result = Result.success(null)
        harness.move(meters = 100.0, seconds = 15.0)
        advanceUntilIdle()

        assertThat(harness.manager.streetName).isNull()
    }

    /**
     * A cancellation is a teardown, not a geocoder failure: it must not arm the exponential
     * backoff (which would delay the next genuine attempt for no reason) and must not be
     * swallowed as a handled error.
     */
    @Test
    fun `a cancelled lookup is not a geocoder failure`() = runTest {
        val lookupJob = Job()
        val harness = Harness(CoroutineScope(coroutineContext + lookupJob))
        harness.geocoder.gate = CompletableDeferred()

        harness.move(meters = 0.0)
        runCurrent() // the lookup is now suspended inside the geocoder
        lookupJob.cancelChildren()
        advanceUntilIdle()

        // The next attempt must be allowed at the base 10 s interval — a failure would have
        // doubled it to 20 s and gated this one out.
        harness.geocoder.gate = null
        harness.move(meters = 100.0, seconds = 12.0)
        advanceUntilIdle()

        assertWithMessage("a cancellation must not arm the failure backoff")
            .that(harness.geocoder.requests).hasSize(2)
        assertThat(harness.manager.streetName).isEqualTo("Storgata")
    }

    /**
     * The guard is set before the launch; a launch into an already-cancelled scope never
     * runs its body *or* its finally, so the reset has to ride the job's completion — else
     * the guard latches true and every future lookup is silently dropped.
     */
    @Test
    fun `a lookup launched into a cancelled scope does not latch the in-progress guard`() = runTest {
        val lookupScope = CoroutineScope(coroutineContext + Job())
        val harness = Harness(lookupScope)
        lookupScope.cancel()

        harness.move(meters = 0.0)
        advanceUntilIdle() // let the stillborn job finalize; the old finally never would

        assertWithMessage("a coroutine that never ran cannot be 'in progress'")
            .that(harness.manager.isLookupInProgress).isFalse()
    }

    /** While a lookup is in flight, further fixes must not start another. */
    @Test
    fun `an in-flight lookup blocks further requests`() = runTest {
        val harness = Harness(this)
        harness.geocoder.gate = CompletableDeferred()

        harness.move(meters = 0.0)
        harness.move(meters = 100.0, seconds = 15.0)
        harness.move(meters = 100.0, seconds = 15.0)

        harness.geocoder.gate!!.complete(Unit)
        advanceUntilIdle()

        assertWithMessage("only the first fix may have started a lookup")
            .that(harness.geocoder.requests).hasSize(1)
        assertThat(harness.manager.streetName).isEqualTo("Storgata")
    }
}
