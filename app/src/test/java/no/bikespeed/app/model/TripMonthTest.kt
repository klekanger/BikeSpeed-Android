package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.ZoneOffset
import no.bikespeed.app.fixtures.dateAtNoonUtc
import org.junit.Test

/**
 * [TripMonth] is the grouping key for the trip log's month sections. The cases that would
 * silently mis-bucket or mis-order a long log — two days in one month, and the
 * December→January boundary — are the ones worth pinning; they're pure `(year, month)`
 * arithmetic, so no UI is involved.
 */
class TripMonthTest {

    private val utc = ZoneOffset.UTC

    @Test
    fun `two dates in the same month share one bucket`() {
        val early = TripMonth.containing(dateAtNoonUtc(2026, 7, 2), utc)
        val late = TripMonth.containing(dateAtNoonUtc(2026, 7, 30), utc)

        assertWithMessage("the 2nd and the 30th of July are the same section")
            .that(early).isEqualTo(late)
        assertThat(early.year).isEqualTo(2026)
        assertThat(early.month).isEqualTo(7)
    }

    /**
     * The trap: comparing month numbers alone puts December (12) after January (1).
     * [TripMonth] orders by year first, so January 2026 is *later* than December 2025 —
     * which is what a newest-first list needs.
     */
    @Test
    fun `january of the next year sorts later than the december before`() {
        val december = TripMonth.containing(dateAtNoonUtc(2025, 12, 31), utc)
        val january = TripMonth.containing(dateAtNoonUtc(2026, 1, 1), utc)

        assertThat(december).isLessThan(january)
        assertThat(listOf(december, january).max()).isEqualTo(january)
    }

    @Test
    fun `representative date lands inside the month it names`() {
        val month = TripMonth.containing(dateAtNoonUtc(2026, 3, 17), utc)

        val representative = month.representativeDate(utc).atZone(utc)
        assertThat(representative.year).isEqualTo(2026)
        assertThat(representative.monthValue).isEqualTo(3)
    }
}
