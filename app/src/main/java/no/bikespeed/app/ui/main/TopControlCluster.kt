package no.bikespeed.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import no.bikespeed.app.ui.theme.GaugePalette

/**
 * The top-right navigation cluster: a single neutral capsule holding the app's screen-level
 * destinations. Chrome, deliberately — translucent and secondary-toned so it recedes and the
 * orange gauge keeps the eye. New screen-level destinations get another button here; past
 * ~three, fold the extras into a menu rather than widening the pill across the gauge.
 */
@Composable
fun TopControlCluster(
    onTripLog: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTripLog, modifier = Modifier.size(width = 40.dp, height = 36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = "Trips",
                tint = GaugePalette.accent,
            )
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(1.dp)
                .height(22.dp)
                .background(Color.White.copy(alpha = 0.15f)),
        )
        IconButton(onClick = onSettings, modifier = Modifier.size(width = 40.dp, height = 36.dp)) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color(0xFF9E9E9E),
            )
        }
    }
}
