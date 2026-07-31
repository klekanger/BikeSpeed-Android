package no.bikespeed.app.fixtures

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import no.bikespeed.app.model.TripLogEntry

/**
 * Noon on a calendar date, UTC throughout — so a case never turns on the machine's time zone
 * shifting a date across midnight. Mirrors the iOS `date(_:_:_:in:)` fixture.
 */
fun dateAtNoonUtc(year: Int, month: Int, day: Int): Instant =
    LocalDate.of(year, month, day).atTime(LocalTime.NOON).atZone(ZoneOffset.UTC).toInstant()

fun makeEntry(
    startDate: Instant,
    distance: Double = 10_000.0,
    duration: Double = 1_800.0,
    averageSpeed: Double = 6.8,
    maxSpeed: Double = 11.4,
    ascent: Double? = null,
): TripLogEntry = TripLogEntry(
    id = UUID.randomUUID(),
    startDate = startDate,
    duration = duration,
    distance = distance,
    averageSpeed = averageSpeed,
    maxSpeed = maxSpeed,
    totalAscent = ascent,
    totalDescent = ascent,
)
