package no.bikespeed.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Named colors shared across the gauge views, mirroring the iOS palette exactly: pure black
 * background, orange accent, near-black instrument faces, brushed-metal bezels.
 */
object GaugePalette {
    /** iOS `.orange`. */
    val accent = Color(0xFFFF9500)

    /** `#1B1B1B` — the gauge face center and the stats panel background. */
    val faceCenter = Color(0xFF1B1B1B)

    val faceEdge = Color(0xFF262626) // Color(white: 0.15)

    /** The speed readout's pill background — Color(white: 0.18). */
    val readoutBackground = Color(0xFF2E2E2E)

    val majorTick = Color.White
    val minorTick = Color(0xFF999999) // Color(white: 0.6)

    /**
     * Brushed-metal ring gradient shared by the speedometer bezel and the stats-panel bezel,
     * so both catch light the same way. Rendered as a sweep gradient, with the angle nudged
     * by device tilt at the call site.
     */
    val metallicBezel: List<Color> = listOf(
        Color(0xFFCCCCCC), // 0.8
        Color(0xFF404040), // 0.25
        Color(0xFFE6E6E6), // 0.9
        Color(0xFF333333), // 0.2
        Color(0xFFA6A6A6), // 0.65
        Color(0xFF4D4D4D), // 0.3
        Color(0xFFCCCCCC), // 0.8
    )
}
