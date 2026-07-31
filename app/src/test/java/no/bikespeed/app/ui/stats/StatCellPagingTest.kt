package no.bikespeed.app.ui.stats

import com.google.common.truth.Truth.assertThat
import no.bikespeed.app.model.TripState
import org.junit.Test

/**
 * The altitude cell's page set is the one piece of the panel that changes shape at runtime:
 * climb and descent are trip stats and only exist once a trip has started. Getting it wrong
 * either shows two permanent "--" readouts or drops them mid-ride, so the page order and both
 * shapes are pinned here — ported from the iOS AltitudeCellPageTests.
 */
class StatCellPagingTest {

    @Test
    fun `an idle trip offers only the live readouts`() {
        assertThat(AltitudeCellPage.pages(TripState.IDLE))
            .containsExactly(AltitudeCellPage.ALTITUDE, AltitudeCellPage.POSITION)
            .inOrder()
    }

    @Test
    fun `a started trip adds climb and descent`() {
        for (state in listOf(TripState.RUNNING, TripState.PAUSED)) {
            assertThat(AltitudeCellPage.pages(state))
                .containsExactly(
                    AltitudeCellPage.ALTITUDE,
                    AltitudeCellPage.CLIMB,
                    AltitudeCellPage.DESCENT,
                    AltitudeCellPage.POSITION,
                )
                .inOrder()
        }
    }

    /**
     * Climb sits next to the altitude it accumulates from, and descent next to climb — the
     * pair reads as one swipe. A reordering here silently rearranges the cell, so it's pinned
     * rather than left to declaration order.
     */
    @Test
    fun `climb and descent sit between altitude and position`() {
        assertThat(AltitudeCellPage.entries)
            .containsExactly(
                AltitudeCellPage.ALTITUDE,
                AltitudeCellPage.CLIMB,
                AltitudeCellPage.DESCENT,
                AltitudeCellPage.POSITION,
            )
            .inOrder()
    }

    /** Wraps at the ends, so a tap on the last page returns to the first. */
    @Test
    fun `paging wraps in both directions`() {
        assertThat(StatCellPaging.advance(page = 1, count = 2, delta = 1)).isEqualTo(0)
        assertThat(StatCellPaging.advance(page = 0, count = 2, delta = -1)).isEqualTo(1)
        assertThat(StatCellPaging.advance(page = 0, count = 4, delta = 1)).isEqualTo(1)
    }
}
