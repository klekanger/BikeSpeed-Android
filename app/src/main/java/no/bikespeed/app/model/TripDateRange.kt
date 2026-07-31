package no.bikespeed.app.model

import java.time.Instant
import java.time.ZoneId

/**
 * The date window the trip-log *list* is narrowed to.
 *
 * A pure value type in the shape of [TripLogSummary]: [contains] takes `now` and a [ZoneId]
 * as arguments rather than reading the system clock/zone, so tests can pin every boundary
 * deterministically instead of testing through the UI. No Compose import: the menu labels
 * live in the view, keeping this at the model layer's SI/value-type altitude.
 *
 * **It filters the list only, never the summary above it.** Lifetime totals and personal
 * bests stay lifetime whatever range is selected — your top speed is your top speed
 * regardless of which window you are looking at.
 */
enum class TripDateRange {
    ALL,
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_YEAR;

    /**
     * Whether a trip that started at [date] falls inside this range as of [now].
     *
     * The day-count windows are *rolling* — "last 7 days" is the seven days up to [now], not a
     * calendar week; the summary's "This week" already covers the calendar-week meaning.
     * "This year" is the calendar year containing [now] in [zone], so it honours the calendar's
     * own year boundary rather than a naive 365-day slice.
     */
    fun contains(date: Instant, now: Instant, zone: ZoneId): Boolean = when (this) {
        ALL -> true
        LAST_7_DAYS -> isWithin(days = 7, date = date, now = now, zone = zone)
        LAST_30_DAYS -> isWithin(days = 30, date = date, now = now, zone = zone)
        THIS_YEAR -> date.atZone(zone).year == now.atZone(zone).year
    }

    private fun isWithin(days: Long, date: Instant, now: Instant, zone: ZoneId): Boolean {
        // Calendar arithmetic (not now - 7*86400 s): minusDays on the zoned time honours DST
        // transitions, matching the iOS Calendar.date(byAdding:) behaviour.
        val start = now.atZone(zone).minusDays(days).toInstant()
        return date >= start && date <= now
    }
}
