package no.bikespeed.app.service

import java.time.Instant

/**
 * The injectable wall clock. `TripManager`'s elapsed time is wall-clock based (correct across
 * backgrounding) and driven through this seam, so tests advance a `TestClock` and call
 * `tick()` deterministically instead of sleeping. Never add a sleep to make a timing test
 * pass.
 */
fun interface NowProvider {
    fun now(): Instant
}
