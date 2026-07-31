package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.DayOfWeek
import java.time.ZoneOffset
import no.bikespeed.app.fixtures.dateAtNoonUtc
import no.bikespeed.app.fixtures.makeEntry
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * [TripLogSummary] is a pure function of `(entries, firstDayOfWeek, zone, now)` — all
 * injected — so every case here is deterministic. That is the whole reason the type exists
 * rather than the arithmetic living in the view: "this week" depends on a calendar and a
 * clock, and neither is testable through the UI.
 */
class TripLogSummaryTest {

    private val utc = ZoneOffset.UTC

    /** Tuesday 14 July 2026 — the day every "this week"/"this month" case below is reasoned from. */
    private val today = dateAtNoonUtc(2026, 7, 14)

    private fun summary(
        entries: List<TripLogEntry>,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ) = TripLogSummary(entries, firstDayOfWeek, utc, today)

    /**
     * The rider who opens the log before their first ride must see zeroes, not an empty screen
     * and not a crash — the view renders the Totals section unconditionally.
     */
    @Test
    fun `an empty log summarizes to zero rather than null`() {
        val summary = summary(emptyList())

        assertThat(summary.allTime.rideCount).isEqualTo(0)
        assertThat(summary.allTime.distance).isWithin(0.5).of(0.0)
        assertThat(summary.allTime.duration).isWithin(0.5).of(0.0)
        assertThat(summary.allTime.ascent).isWithin(0.5).of(0.0)
        assertThat(summary.personalBests.longestRide).isNull()
        assertThat(summary.personalBests.biggestClimb).isNull()
    }

    @Test
    fun `lifetime totals sum every ride`() {
        val entries = listOf(
            makeEntry(startDate = dateAtNoonUtc(2026, 7, 14), distance = 10_000.0, duration = 1_800.0, ascent = 120.0),
            makeEntry(startDate = dateAtNoonUtc(2026, 3, 2), distance = 5_000.0, duration = 900.0, ascent = 40.0),
        )

        val summary = summary(entries)

        assertThat(summary.allTime.rideCount).isEqualTo(2)
        assertThat(summary.allTime.distance).isWithin(0.5).of(15_000.0)
        assertThat(summary.allTime.duration).isWithin(0.5).of(2_700.0)
        assertThat(summary.allTime.ascent).isWithin(0.5).of(160.0)
    }

    /**
     * A trip recorded before elevation shipped has no ascent figure at all — that is not the
     * same as having climbed nothing, and counting it as zero would quietly drag the lifetime
     * total down.
     */
    @Test
    fun `rides with no ascent figure are skipped rather than counted as flat`() {
        val entries = listOf(
            makeEntry(startDate = dateAtNoonUtc(2026, 7, 14), distance = 10_000.0, ascent = 120.0),
            makeEntry(startDate = dateAtNoonUtc(2026, 7, 14), distance = 10_000.0, ascent = null),
        )

        val summary = summary(entries)

        assertThat(summary.allTime.ascent).isWithin(0.5).of(120.0)
        assertWithMessage("the ride still happened — only its climb is unknown")
            .that(summary.allTime.rideCount).isEqualTo(2)
        assertThat(summary.allTime.distance).isWithin(0.5).of(20_000.0)
    }

    /**
     * **The nb_NO trap.** "This week" is not a fixed seven days back: it starts on the
     * calendar's first weekday, which is Monday in Norway and Sunday in the US. The same
     * Sunday ride therefore belongs to a different week depending on the locale, and a naive
     * implementation gets one of the two wrong.
     *
     * `now` here is Tuesday 14 July 2026. With a Monday-start week the week began on the 13th,
     * so the ride on Sunday the 12th belongs to *last* week. With a Sunday-start week the week
     * began on the 12th, so the very same ride is in *this* one.
     */
    @Test
    fun `this week starts on the calendar's first weekday rather than seven days back`() {
        val sundayRide = makeEntry(startDate = dateAtNoonUtc(2026, 7, 12), distance = 8_000.0)

        val norwegian = summary(listOf(sundayRide), firstDayOfWeek = DayOfWeek.MONDAY)
        val american = summary(listOf(sundayRide), firstDayOfWeek = DayOfWeek.SUNDAY)

        assertWithMessage("a Monday-start week beginning the 13th excludes the 12th")
            .that(norwegian.thisWeek.rideCount).isEqualTo(0)
        assertWithMessage("a Sunday-start week beginning the 12th includes it")
            .that(american.thisWeek.rideCount).isEqualTo(1)
        assertWithMessage("and the ride is in the lifetime total either way")
            .that(norwegian.allTime.rideCount).isEqualTo(1)
    }

    @Test
    fun `this week counts a ride inside the current week`() {
        val entries = listOf(
            makeEntry(startDate = dateAtNoonUtc(2026, 7, 13), distance = 8_000.0, duration = 1_200.0), // Monday
            makeEntry(startDate = dateAtNoonUtc(2026, 7, 6), distance = 4_000.0, duration = 600.0), // last Monday
        )

        val summary = summary(entries)

        assertThat(summary.thisWeek.rideCount).isEqualTo(1)
        assertThat(summary.thisWeek.distance).isWithin(0.5).of(8_000.0)
        assertThat(summary.thisWeek.duration).isWithin(0.5).of(1_200.0)
    }

    @Test
    fun `this month excludes the ride on the last day of the month before`() {
        val entries = listOf(
            makeEntry(startDate = dateAtNoonUtc(2026, 7, 1), distance = 3_000.0),
            makeEntry(startDate = dateAtNoonUtc(2026, 6, 30), distance = 9_000.0),
        )

        val summary = summary(entries)

        assertThat(summary.thisMonth.rideCount).isEqualTo(1)
        assertThat(summary.thisMonth.distance).isWithin(0.01).of(3_000.0)
    }

    @Test
    fun `personal bests pick the right ride for each category`() {
        val long = makeEntry(startDate = dateAtNoonUtc(2026, 7, 1), distance = 80_000.0, averageSpeed = 5.0, maxSpeed = 9.0, ascent = 100.0)
        val fast = makeEntry(startDate = dateAtNoonUtc(2026, 7, 2), distance = 20_000.0, averageSpeed = 9.0, maxSpeed = 12.0, ascent = 50.0)
        val quick = makeEntry(startDate = dateAtNoonUtc(2026, 7, 3), distance = 10_000.0, averageSpeed = 6.0, maxSpeed = 18.0, ascent = 20.0)
        val hilly = makeEntry(startDate = dateAtNoonUtc(2026, 7, 4), distance = 30_000.0, averageSpeed = 4.0, maxSpeed = 10.0, ascent = 900.0)

        val bests = summary(listOf(long, fast, quick, hilly)).personalBests

        assertThat(bests.longestRide?.id).isEqualTo(long.id)
        assertThat(bests.fastestAverage?.id).isEqualTo(fast.id)
        assertThat(bests.highestMaxSpeed?.id).isEqualTo(quick.id)
        assertThat(bests.biggestClimb?.id).isEqualTo(hilly.id)
    }

    /**
     * A trip with no ascent figure cannot hold the climbing record — and when *no* trip has
     * one, there is no record to show rather than a fictional 0 m best.
     */
    @Test
    fun `the climbing record ignores rides that never recorded ascent`() {
        val climbed = makeEntry(startDate = dateAtNoonUtc(2026, 7, 1), distance = 10_000.0, ascent = 250.0)
        val unknown = makeEntry(startDate = dateAtNoonUtc(2026, 7, 2), distance = 90_000.0, ascent = null)

        assertThat(summary(listOf(climbed, unknown)).personalBests.biggestClimb?.id)
            .isEqualTo(climbed.id)
        assertWithMessage("no trip has climbed, so there is no climbing record — not a 0 m one")
            .that(summary(listOf(unknown)).personalBests.biggestClimb).isNull()
    }

    /**
     * Two rides of exactly the same length is an ordinary thing (a commute, ridden twice). It
     * must pick one and carry on, not trap on an ambiguous maximum.
     */
    @Test
    fun `tied personal bests pick one ride rather than failing`() {
        val first = makeEntry(startDate = dateAtNoonUtc(2026, 7, 1), distance = 12_000.0)
        val second = makeEntry(startDate = dateAtNoonUtc(2026, 7, 2), distance = 12_000.0)

        val best = summary(listOf(first, second)).personalBests.longestRide
        assertNotNull(best)

        assertThat(listOf(first.id, second.id)).contains(best!!.id)
    }
}
