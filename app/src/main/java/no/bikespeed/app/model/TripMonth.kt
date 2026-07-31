package no.bikespeed.app.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The calendar month a trip belongs to — the bucket the trip-log list groups its rows under,
 * so a long log reads as "July 2026 / June 2026 / …" section cards rather than one unbroken
 * scroll.
 *
 * A `(year, month)` pair, not a date: two rides on different days of the same month must land
 * in the *same* bucket, and comparing whole months is what orders the sections. Built from an
 * injected [ZoneId] (region-based, like the summary's calendar) so the month boundary is the
 * rider's, and pure/[Comparable] so tests can pin the year-boundary ordering without any UI.
 */
data class TripMonth(val year: Int, val month: Int) : Comparable<TripMonth> {

    /**
     * Chronological: an earlier month is "less than" a later one, so December 2025 precedes
     * January 2026 rather than sorting after it on the month number alone. The list sorts
     * sections in reverse for newest-first.
     */
    override fun compareTo(other: TripMonth): Int =
        compareValuesBy(this, other, TripMonth::year, TripMonth::month)

    /**
     * A stand-in [Instant] (the 1st, noon) for formatting a localized "MMMM yyyy" header —
     * the view has only the year/month, but a date formatter needs a date to localize the
     * month name against.
     */
    fun representativeDate(zone: ZoneId): Instant =
        LocalDate.of(year, month, 1).atTime(LocalTime.NOON).atZone(zone).toInstant()

    companion object {
        fun containing(date: Instant, zone: ZoneId): TripMonth {
            val local = date.atZone(zone)
            return TripMonth(year = local.year, month = local.monthValue)
        }
    }
}
