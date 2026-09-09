package no.bikespeed.app.ui.main

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The main screen's vertical budget.
 *
 * Everything below the gauge is fixed: the stats panel is 260 dp, the control bar is
 * [TripControlBarHeight], and the gaps between them are what keeps the screen readable at
 * arm's length on a handlebar. The gauge is the one element that can be any size, so it —
 * not the spacing — absorbs whatever a short safe area leaves over. A phone with a
 * three-button navigation bar (e.g. an Android 10 Xperia: 411 × 823 dp, of which
 * `safeDrawingPadding` takes ~72 dp for the status and navigation bars) is ~60 dp short of
 * the roomy layout; laying the pieces out at their nominal sizes there crushed the control
 * bar against the stats panel, because the slack came out of the one spacer that had any
 * give.
 *
 * Pure Dp arithmetic so it can be checked without a device — see MainScreenLayoutTest.
 */
internal object MainScreenLayout {

    /** Inset on each side of the gauge; caps how wide it can grow. */
    val GaugeHorizontalPadding = 12.dp

    /** Gauge to stats panel. */
    val GaugeToStats = 10.dp

    val StatsHeight = 260.dp

    /** The gap the control bar is guaranteed, however short the screen. */
    val MinStatsToControls = 16.dp

    /** Below the control bar, inside the safe area. */
    val ControlBarBottomPadding = 12.dp

    /** Breathing room above the gauge on a roomy screen; the first thing to be given up. */
    val MaxTopSpacing = 40.dp

    /** …but never all of it: the gauge would run into the GPS/settings cluster. */
    val MinTopSpacing = 8.dp

    /**
     * [width] and [height] are the safe area — the constraints inside `safeDrawingPadding`,
     * i.e. already minus the status and navigation bars.
     */
    fun forSafeArea(width: Dp, height: Dp): MainScreenMetrics {
        val reservedBelowGauge =
            GaugeToStats + StatsHeight + MinStatsToControls + TripControlBarHeight +
                ControlBarBottomPadding
        val forTopAndGauge = (height - reservedBelowGauge).coerceAtLeast(0.dp)
        val gaugeSide = minOf(
            width - GaugeHorizontalPadding * 2,
            forTopAndGauge - MinTopSpacing,
        ).coerceAtLeast(0.dp)
        // Whatever the square gauge didn't take: up to MaxTopSpacing above it, the rest
        // (a tall screen) left to the flexible spacer above the control bar.
        val topSpacing = (forTopAndGauge - gaugeSide).coerceIn(0.dp, MaxTopSpacing)
        return MainScreenMetrics(topSpacing = topSpacing, gaugeSide = gaugeSide)
    }
}

/** What [MainScreenLayout.forSafeArea] resolved for the screen it was handed. */
internal data class MainScreenMetrics(
    val topSpacing: Dp,
    val gaugeSide: Dp,
)
