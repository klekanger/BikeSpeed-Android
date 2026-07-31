package no.bikespeed.app.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.bikespeed.app.model.GpsSignalQuality

/**
 * Top-left GPS signal indicator: a location glyph tinted green (good), yellow (fair), or red
 * (poor). Poor shows the crossed-out glyph, since in that state speed and distance are frozen
 * (see `LocationManager`). While a trip runs, the glyph pulses slowly with a "Logging" label.
 */
@Composable
fun GpsSignalIndicator(
    quality: GpsSignalQuality,
    isTracking: Boolean,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "gpsPulse")
    val pulsingAlpha by pulse.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gpsPulseAlpha",
    )
    val alpha = if (isTracking) pulsingAlpha else 0.75f

    val color = when (quality) {
        GpsSignalQuality.GOOD -> Color(0xFF34C759)
        GpsSignalQuality.FAIR -> Color(0xFFFFCC00)
        GpsSignalQuality.POOR -> Color(0xFFFF3B30)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (quality == GpsSignalQuality.POOR) Icons.Filled.GpsOff else Icons.Filled.GpsFixed,
            contentDescription = "GPS signal",
            tint = color,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { this.alpha = alpha },
        )
        if (isTracking) {
            Text(
                text = "Logging",
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.graphicsLayer { this.alpha = alpha },
            )
        }
    }
}
