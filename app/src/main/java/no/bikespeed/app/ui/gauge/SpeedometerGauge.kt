package no.bikespeed.app.ui.gauge

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import no.bikespeed.app.model.MeasurementSystem
import no.bikespeed.app.ui.theme.BikeSpeedTheme
import no.bikespeed.app.ui.theme.GaugePalette

/**
 * The 80s-car-style analog gauge: static face (dial, ticks, danger arc), rotating needle,
 * hub, and centre unit/speed readout. Geometry comes from [GaugeGeometry]; this file only
 * draws it.
 *
 * [tilt] is a small, heavily-smoothed accelerometer vector that nudges the metallic gradients
 * so the bezel/face catch light as the phone moves — zero until the platform sensor glue
 * lands.
 */
@Composable
fun SpeedometerGauge(
    speedMetersPerSecond: Double,
    maxGaugeSpeedKmh: Double,
    measurementSystem: MeasurementSystem,
    locale: Locale,
    modifier: Modifier = Modifier,
    tilt: Offset = Offset.Zero,
) {
    val maxSpeedDisplay = measurementSystem.maxGaugeSpeedValue(fromCanonicalKmh = maxGaugeSpeedKmh)
    val speedDisplay = measurementSystem.speedValue(speedMetersPerSecond)

    val needleAngle by animateFloatAsState(
        targetValue = GaugeGeometry.needleAngle(speedDisplay, maxSpeedDisplay).toFloat(),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "needleAngle",
    )

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val side: Dp = minOf(maxWidth, maxHeight)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GaugeFace(
                maxSpeed = maxSpeedDisplay,
                tilt = tilt,
                modifier = Modifier.fillMaxSize(),
            )

            Needle(
                modifier = Modifier
                    .size(side)
                    .graphicsLayer { rotationZ = needleAngle },
            )

            Hub(side = side, tilt = tilt)

            CenterReadout(
                side = side,
                speedDisplay = speedDisplay,
                unitLabel = measurementSystem.gaugeUnitLabel(locale),
                modifier = Modifier.offset(y = side * 0.23f),
            )
        }
    }
}

/**
 * The static dial: background, bezel, danger arc, tick marks and labels. Recomposes (and
 * redraws) only when [maxSpeed] or [tilt] change — never per GPS fix.
 */
@Composable
private fun GaugeFace(
    maxSpeed: Double,
    tilt: Offset,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(size.width, size.height) / 2f * 0.92f

        // Face: radial gradient whose light center drifts with tilt.
        val lightCenter = Offset(
            centerX + tilt.x * radius * 0.35f,
            centerY + tilt.y * radius * 0.35f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GaugePalette.faceEdge, GaugePalette.faceCenter),
                center = lightCenter,
                radius = radius,
            ),
            radius = radius,
            center = Offset(centerX, centerY),
        )

        // Bezel: brushed-metal sweep gradient ring.
        drawCircle(
            brush = Brush.sweepGradient(
                colors = GaugePalette.metallicBezel,
                center = Offset(centerX, centerY),
            ),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = radius * 0.05f),
        )

        // Danger arc: a polyline over the top 15 % of the dial.
        val arcRadius = radius * 0.92f
        val steps = 24
        val dangerPath = Path()
        for (i in 0..steps) {
            val fraction = GaugeGeometry.DANGER_START_FRACTION +
                (1 - GaugeGeometry.DANGER_START_FRACTION) * i / steps
            val (x, y) = GaugeGeometry.pointOnDial(
                centerX, centerY, arcRadius, GaugeGeometry.angleForFraction(fraction),
            )
            if (i == 0) dangerPath.moveTo(x, y) else dangerPath.lineTo(x, y)
        }
        drawPath(
            path = dangerPath,
            color = GaugePalette.accent,
            style = Stroke(width = radius * 0.05f, cap = StrokeCap.Round),
        )

        // Ticks and labels.
        for (tick in GaugeGeometry.ticks(maxSpeed)) {
            val outer = radius * 0.88f
            val inner = if (tick.isMajor) radius * 0.76f else radius * 0.82f
            val (x1, y1) = GaugeGeometry.pointOnDial(centerX, centerY, outer, tick.angleDegrees)
            val (x2, y2) = GaugeGeometry.pointOnDial(centerX, centerY, inner, tick.angleDegrees)

            val color = when {
                tick.isDanger -> GaugePalette.accent
                tick.isMajor -> GaugePalette.majorTick
                else -> GaugePalette.minorTick
            }
            drawLine(
                color = color,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = if (tick.isMajor) radius * 0.02f else radius * 0.01f,
            )

            if (tick.isMajor) {
                val (lx, ly) = GaugeGeometry.pointOnDial(centerX, centerY, radius * 0.64f, tick.angleDegrees)
                val label = GaugeGeometry.formattedTickLabel(tick.value)
                val layout = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = with(density) { (radius * 0.16f).toSp() },
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(lx - layout.size.width / 2f, ly - layout.size.height / 2f),
                )
            }
        }
    }
}

/**
 * A tapered needle pointing straight up, rotated by the caller about its own center (which
 * coincides with the gauge pivot).
 */
@Composable
private fun Needle(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val tipLength = size.height * 0.42f
        val tailLength = size.height * 0.06f
        val baseWidth = size.width * 0.055f

        val path = Path().apply {
            moveTo(centerX - baseWidth / 2f, centerY)
            lineTo(centerX, centerY - tipLength)
            lineTo(centerX + baseWidth / 2f, centerY)
            lineTo(centerX, centerY + tailLength)
            close()
        }
        drawPath(path = path, color = GaugePalette.accent)
    }
}

/** The polished pivot cap, its highlight drifting with tilt. */
@Composable
private fun Hub(side: Dp, tilt: Offset) {
    Canvas(modifier = Modifier.size(side * 0.12f)) {
        val radius = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFCCCCCC), Color(0xFF666666), Color(0xFF1F1F1F)),
                center = Offset(
                    size.width * (0.35f + tilt.x * 0.28f),
                    size.height * (0.32f + tilt.y * 0.28f),
                ),
                radius = radius * 1.2f,
            ),
            radius = radius,
        )
        drawCircle(
            color = Color(0xFF8C8C8C), // white 0.55
            radius = radius,
            style = Stroke(width = size.minDimension * 0.033f),
        )
    }
}

@Composable
private fun CenterReadout(
    side: Dp,
    speedDisplay: Double,
    unitLabel: String,
    modifier: Modifier = Modifier,
) {
    val unitSize = with(LocalDensity.current) { (side * 0.05f).toSp() }
    val speedSize = with(LocalDensity.current) { (side * 0.08f).toSp() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(side * 0.015f),
    ) {
        Text(
            text = unitLabel,
            fontSize = unitSize,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9E9E9E),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = speedDisplay.toInt().toString(),
            fontSize = speedSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = GaugePalette.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(side * 0.22f) // fixed width leaving room for 3 digits
                .background(GaugePalette.readoutBackground, RoundedCornerShape(side * 0.03f))
                .padding(vertical = side * 0.012f),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 360)
@Composable
private fun SpeedometerGaugePreview() {
    BikeSpeedTheme {
        SpeedometerGauge(
            speedMetersPerSecond = 10.0,
            maxGaugeSpeedKmh = 60.0,
            measurementSystem = MeasurementSystem.METRIC,
            locale = Locale.forLanguageTag("en"),
            modifier = Modifier.padding(24.dp),
        )
    }
}
