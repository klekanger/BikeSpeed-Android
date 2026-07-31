package no.bikespeed.app.ui.log

import java.time.Instant
import java.time.ZoneId
import no.bikespeed.app.model.TripDateRange
import no.bikespeed.app.model.TripLogEntry
import no.bikespeed.app.model.TripMonth

/**
 * The list pipeline, extracted pure: filter by the selected [TripDateRange] (the list only —
 * never the summary above it), then group into calendar-month sections, newest month first.
 * Rows arrive newest-first from the database and `groupBy` preserves that order within each
 * bucket, so only the sections themselves need sorting.
 */
object TripLogGrouping {

    data class MonthSection(val month: TripMonth, val trips: List<TripLogEntry>)

    fun sections(
        entries: List<TripLogEntry>,
        range: TripDateRange,
        now: Instant,
        zone: ZoneId,
    ): List<MonthSection> {
        val visible = entries.filter { range.contains(it.startDate, now, zone) }
        return visible
            .groupBy { TripMonth.containing(it.startDate, zone) }
            .map { (month, trips) -> MonthSection(month, trips) }
            .sortedByDescending { it.month }
    }
}
