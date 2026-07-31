package no.bikespeed.app.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import no.bikespeed.app.ui.theme.GaugePalette

/**
 * A minimal area + line chart against distance — hand-rolled rather than a charting library:
 * two fixed-style charts over a ≤500-point pre-decimated series don't justify one. The X axis
 * is the trip's accumulated distance, shared by the height and speed charts so the two read
 * against one another.
 */
@Composable
fun DistanceChart(
    points: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val minX = points.first().first
        val maxX = points.last().first
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
        // A flat series still needs a visible line — pad a degenerate Y span.
        val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0

        fun x(value: Double): Float = ((value - minX) / spanX * size.width).toFloat()
        fun y(value: Double): Float = (size.height - (value - minY) / spanY * size.height * 0.9f - size.height * 0.05f).toFloat()

        val line = Path()
        points.forEachIndexed { index, (distance, value) ->
            if (index == 0) line.moveTo(x(distance), y(value)) else line.lineTo(x(distance), y(value))
        }

        val area = Path().apply {
            addPath(line)
            lineTo(x(maxX), size.height)
            lineTo(x(minX), size.height)
            close()
        }

        drawPath(path = area, color = GaugePalette.accent.copy(alpha = 0.3f))
        drawPath(path = line, color = GaugePalette.accent, style = Stroke(width = 3f))
    }
}
