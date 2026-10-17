package no.bikespeed.app.ui.main

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The main screen's vertical budget on the screens that squeeze it. The regression these pin
 * is an Android 10 Xperia (411 × 823 dp) with a three-button navigation bar: the trip control
 * bar ended up flush against the stats panel because the layout laid the gauge out at its
 * nominal full width first and left the spacing to absorb the shortfall.
 */
class MainScreenLayoutTest {

    /** What the column consumes below the gauge, all of it fixed. */
    private val reservedBelowGauge =
        MainScreenLayout.GaugeToStats + MainScreenLayout.StatsHeight +
            MainScreenLayout.MinStatsToControls + TripControlBarHeight +
            MainScreenLayout.ControlBarBottomPadding

    private fun usedHeight(metrics: MainScreenMetrics) =
        metrics.topSpacing + metrics.gaugeSide + reservedBelowGauge

    @Test
    fun `a three-button navigation bar takes its space from the gauge, not the spacing`() {
        // 823 dp tall, minus a 24 dp status bar and a 48 dp navigation bar.
        val metrics = MainScreenLayout.forSafeArea(width = 411.dp, height = 751.dp)

        assertThat(usedHeight(metrics)).isAtMost(751.dp)
        // Everything below the gauge still gets its full nominal height — including the gap
        // above the control bar.
        assertThat(metrics.topSpacing).isAtLeast(MainScreenLayout.MinTopSpacing)
        assertThat(metrics.gaugeSide).isGreaterThan(0.dp)
    }

    @Test
    fun `a gesture-navigation screen keeps the roomy top spacing and a full-width gauge`() {
        val metrics = MainScreenLayout.forSafeArea(width = 411.dp, height = 799.dp)

        assertThat(metrics.topSpacing).isEqualTo(MainScreenLayout.MaxTopSpacing)
        assertThat(metrics.gaugeSide)
            .isEqualTo(411.dp - MainScreenLayout.GaugeHorizontalPadding * 2)
        assertThat(usedHeight(metrics)).isAtMost(799.dp)
    }

    @Test
    fun `the gauge never grows past the screen's width however tall the screen`() {
        val metrics = MainScreenLayout.forSafeArea(width = 411.dp, height = 1200.dp)

        assertThat(metrics.gaugeSide)
            .isEqualTo(411.dp - MainScreenLayout.GaugeHorizontalPadding * 2)
        // The leftover goes to the flexible spacer above the control bar, not to the top.
        assertThat(metrics.topSpacing).isEqualTo(MainScreenLayout.MaxTopSpacing)
    }

    @Test
    fun `a short screen shrinks the gauge and keeps the fixed rows intact`() {
        // A 360 × 640 dp phone, minus status and navigation bars.
        val metrics = MainScreenLayout.forSafeArea(width = 360.dp, height = 568.dp)

        assertThat(usedHeight(metrics)).isAtMost(568.dp)
        assertThat(metrics.gaugeSide).isLessThan(360.dp)
        assertThat(metrics.gaugeSide).isGreaterThan(0.dp)
        assertThat(metrics.topSpacing).isAtLeast(MainScreenLayout.MinTopSpacing)
    }

    @Test
    fun `an absurdly short screen degrades to a zero-size gauge rather than a negative one`() {
        val metrics = MainScreenLayout.forSafeArea(width = 360.dp, height = 200.dp)

        assertThat(metrics.gaugeSide).isEqualTo(0.dp)
        assertThat(metrics.topSpacing).isAtLeast(0.dp)
    }
}
