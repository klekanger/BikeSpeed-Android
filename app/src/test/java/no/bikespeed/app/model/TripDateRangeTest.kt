package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import java.time.ZoneOffset
import no.bikespeed.app.fixtures.dateAtNoonUtc
import org.junit.Test

/**
 * [TripDateRange.contains] is a pure function of `(date, now, zone)`, all injected — so, like
 * [TripLogSummaryTest], every case here is deterministic and pins a boundary. The boundaries
 * are the whole reason the logic lives in a value type rather than inline in the list view.
 */
class TripDateRangeTest {

    private val utc = ZoneOffset.UTC

    /** Tuesday 14 July 2026, noon — the day every window here is reasoned from. */
    private val now = dateAtNoonUtc(2026, 7, 14)

    @Test
    fun `all accepts every date however far away`() {
        val ancient = dateAtNoonUtc(1998, 1, 1)
        val future = dateAtNoonUtc(2099, 12, 31)

        assertThat(TripDateRange.ALL.contains(ancient, now, utc)).isTrue()
        assertThat(TripDateRange.ALL.contains(future, now, utc)).isTrue()
    }

    /**
     * The rolling seven-day window is inclusive at its far edge: a ride exactly seven days
     * before `now` still counts, and one a day older does not.
     */
    @Test
    fun `last 7 days includes the ride exactly seven days back and excludes the one beyond`() {
        val sixDaysAgo = dateAtNoonUtc(2026, 7, 8)
        val exactlySevenDaysAgo = dateAtNoonUtc(2026, 7, 7)
        val eightDaysAgo = dateAtNoonUtc(2026, 7, 6)

        assertThat(TripDateRange.LAST_7_DAYS.contains(sixDaysAgo, now, utc)).isTrue()
        assertThat(TripDateRange.LAST_7_DAYS.contains(exactlySevenDaysAgo, now, utc)).isTrue()
        assertThat(TripDateRange.LAST_7_DAYS.contains(eightDaysAgo, now, utc)).isFalse()
    }

    /**
     * A future-dated trip (clock skew, a fix stamped ahead of `now`) is outside a window that
     * ends at `now` — the window looks back, not forward.
     */
    @Test
    fun `last 7 days excludes a trip dated after now`() {
        val tomorrow = dateAtNoonUtc(2026, 7, 15)

        assertThat(TripDateRange.LAST_7_DAYS.contains(tomorrow, now, utc)).isFalse()
    }

    @Test
    fun `last 30 days spans thirty days back but not thirty-one`() {
        assertThat(TripDateRange.LAST_30_DAYS.contains(dateAtNoonUtc(2026, 6, 14), now, utc)).isTrue()
        assertThat(TripDateRange.LAST_30_DAYS.contains(dateAtNoonUtc(2026, 6, 13), now, utc)).isFalse()
    }

    /**
     * "This year" is the calendar year containing `now`, so 1 January of that year is in and
     * 31 December of the year before is out — even though the latter is well within the last
     * few months.
     */
    @Test
    fun `this year is the calendar year not a rolling window`() {
        assertThat(TripDateRange.THIS_YEAR.contains(dateAtNoonUtc(2026, 1, 1), now, utc)).isTrue()
        assertThat(TripDateRange.THIS_YEAR.contains(dateAtNoonUtc(2026, 12, 31), now, utc)).isTrue()
        assertThat(TripDateRange.THIS_YEAR.contains(dateAtNoonUtc(2025, 12, 31), now, utc)).isFalse()
    }
}
