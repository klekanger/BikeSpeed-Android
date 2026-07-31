package no.bikespeed.app.ui.stats

import no.bikespeed.app.model.TripState

/**
 * The pure paging arithmetic StatCell drives — extracted so the wrap-around and the altitude
 * cell's state-dependent page set are pinned by JVM tests, exactly as the iOS suite pins
 * `AltitudeCellPage`.
 */
object StatCellPaging {
    /** Wraps at the ends, so a tap on the last page returns to the first. */
    fun advance(page: Int, count: Int, delta: Int): Int = (page + delta + count) % count
}

/**
 * The altitude cell's readouts, in page order. Climb and descent are *trip* stats, so they
 * only appear once a trip has started: before that they have nothing to report and would sit
 * at "--" for the whole ride-up, two dead pages between the two live ones. They stay for a
 * paused trip — a rider stopped at the top of a hill hasn't stopped caring how much they
 * climbed, and it's the number to look at before saving.
 */
enum class AltitudeCellPage {
    ALTITUDE,
    CLIMB,
    DESCENT,
    POSITION;

    companion object {
        fun pages(tripState: TripState): List<AltitudeCellPage> =
            if (tripState == TripState.IDLE) listOf(ALTITUDE, POSITION) else entries.toList()
    }
}
