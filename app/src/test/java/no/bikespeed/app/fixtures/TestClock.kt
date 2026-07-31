package no.bikespeed.app.fixtures

import java.time.Instant
import no.bikespeed.app.service.NowProvider

/**
 * A hand-driven wall clock. Tests advance it and call `tripManager.tick()` deterministically —
 * never add a sleep to make a timing test pass.
 */
class TestClock(
    start: Instant = Instant.ofEpochSecond(1_700_000_000),
) : NowProvider {

    var now: Instant = start
        private set

    override fun now(): Instant = now

    fun advance(seconds: Double) {
        now = now.plusMillis((seconds * 1000).toLong())
    }
}
