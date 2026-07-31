package no.bikespeed.app.ui.log

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.ZoneOffset
import no.bikespeed.app.fixtures.dateAtNoonUtc
import no.bikespeed.app.fixtures.makeEntry
import no.bikespeed.app.model.TripDateRange
import no.bikespeed.app.model.TripMonth
import org.junit.Test

class TripLogGroupingTest {

    private val utc = ZoneOffset.UTC
    private val now = dateAtNoonUtc(2026, 7, 14)

    @Test
    fun `sections group by month, newest month first`() {
        val entries = listOf(
            makeEntry(startDate = dateAtNoonUtc(2026, 7, 10)),
            makeEntry(startDate = dateAtNoonUtc(2026, 7, 2)),
            makeEntry(startDate = dateAtNoonUtc(2026, 6, 30)),
        )

        val sections = TripLogGrouping.sections(entries, TripDateRange.ALL, now, utc)

        assertThat(sections.map { it.month })
            .containsExactly(TripMonth(2026, 7), TripMonth(2026, 6))
            .inOrder()
        assertThat(sections.first().trips).hasSize(2)
    }

    /** Rows arrive newest-first and must stay that way within each month bucket. */
    @Test
    fun `order within a month is preserved`() {
        val newer = makeEntry(startDate = dateAtNoonUtc(2026, 7, 10))
        val older = makeEntry(startDate = dateAtNoonUtc(2026, 7, 2))

        val sections = TripLogGrouping.sections(listOf(newer, older), TripDateRange.ALL, now, utc)

        assertThat(sections.single().trips.map { it.id })
            .containsExactly(newer.id, older.id)
            .inOrder()
    }

    /** The filter narrows the list; entries outside the window disappear entirely. */
    @Test
    fun `the date range filters the sections`() {
        val recent = makeEntry(startDate = dateAtNoonUtc(2026, 7, 10))
        val old = makeEntry(startDate = dateAtNoonUtc(2026, 3, 1))

        val sections = TripLogGrouping.sections(listOf(recent, old), TripDateRange.LAST_7_DAYS, now, utc)

        assertThat(sections).hasSize(1)
        assertThat(sections.single().trips.map { it.id }).containsExactly(recent.id)
    }

    @Test
    fun `a window admitting nothing yields no sections`() {
        val old = makeEntry(startDate = dateAtNoonUtc(2025, 3, 1))

        val sections = TripLogGrouping.sections(listOf(old), TripDateRange.LAST_7_DAYS, now, utc)

        assertWithMessage("the log isn't empty, this window is")
            .that(sections).isEmpty()
    }
}
