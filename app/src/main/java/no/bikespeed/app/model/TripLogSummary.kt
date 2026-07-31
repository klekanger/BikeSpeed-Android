package no.bikespeed.app.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Everything the trip log shows *about* the trips rather than the trips themselves: the
 * odometer, the last week and month, and the rider's personal bests.
 *
 * A pure function of `(entries, firstDayOfWeek, zone, now)`, all injected. The week start and
 * `now` both matter: "this week" starts on the calendar's first weekday — Monday in Norway,
 * Sunday in the US — so the same Sunday ride falls in different weeks by locale, and "this
 * week" is meaningless without being told when now is. Injecting them rather than reading
 * system defaults is what makes this testable; the tests pin the weekday case in particular.
 *
 * Distances stay in SI units — the list view converts for display.
 */
class TripLogSummary(
    entries: List<TripLogEntry>,
    firstDayOfWeek: DayOfWeek,
    zone: ZoneId,
    now: Instant,
) {

    /**
     * A zeroed total is the honest answer for a rider with no rides, letting the view render
     * the section unconditionally rather than branch on emptiness.
     */
    data class Totals(
        val rideCount: Int = 0,
        /** Meters. */
        val distance: Double = 0.0,
        /** Seconds. */
        val duration: Double = 0.0,
        /**
         * Summed only over trips that *have* an ascent figure; a pre-elevation trip's unknown
         * climb is not the same as zero — see [TripLogEntry].
         */
        val ascent: Double = 0.0,
    )

    /**
     * Each is null until there's a ride to hold it. [biggestClimb] stays null for a log of
     * trips that never recorded altitude: no climbing record, rather than a fictional 0 m one.
     */
    data class PersonalBests(
        val longestRide: TripLogEntry? = null,
        val fastestAverage: TripLogEntry? = null,
        val highestMaxSpeed: TripLogEntry? = null,
        val biggestClimb: TripLogEntry? = null,
    )

    val allTime: Totals
    val thisWeek: Totals
    val thisMonth: Totals
    val personalBests: PersonalBests

    init {
        allTime = totals(entries)

        // The calendar unit containing `now`, honouring the injected first weekday — exactly
        // what "seven days back" would get wrong.
        val today = now.atZone(zone).toLocalDate()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        thisWeek = totals(
            entries.filter {
                it.startDate.inInterval(
                    from = weekStart.atStartOfDay(zone).toInstant(),
                    until = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant(),
                )
            }
        )

        val monthStart = today.withDayOfMonth(1)
        thisMonth = totals(
            entries.filter {
                it.startDate.inInterval(
                    from = monthStart.atStartOfDay(zone).toInstant(),
                    until = monthStart.plusMonths(1).atStartOfDay(zone).toInstant(),
                )
            }
        )

        personalBests = PersonalBests(
            longestRide = entries.maxByOrNull { it.distance },
            fastestAverage = entries.maxByOrNull { it.averageSpeed },
            highestMaxSpeed = entries.maxByOrNull { it.maxSpeed },
            // Only trips that recorded a climb can hold the record — an unknown ascent must
            // not win by being treated as 0, nor lose to one that did nothing.
            biggestClimb = entries.filter { it.totalAscent != null }.maxByOrNull { it.totalAscent!! },
        )
    }

    private fun totals(entries: List<TripLogEntry>) = Totals(
        rideCount = entries.size,
        distance = entries.sumOf { it.distance },
        duration = entries.sumOf { it.duration },
        ascent = entries.sumOf { it.totalAscent ?: 0.0 },
    )

    private fun Instant.inInterval(from: Instant, until: Instant): Boolean =
        this >= from && this < until
}
