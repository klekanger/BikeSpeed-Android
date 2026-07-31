package no.bikespeed.app.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import no.bikespeed.app.model.ContinuousBearing
import no.bikespeed.app.ui.theme.GaugePalette

/** What the caption line under the value displays. */
sealed interface StatCellCaption {
    /** The stat's own name — "Distance", "Altitude". The default for most cells. */
    data object Name : StatCellCaption

    /**
     * Live data in place of the name, as the direction cell shows the street being ridden.
     * Null renders a blank line, not no line, so the cell stays vertically aligned until data
     * resolves.
     */
    data class Data(val text: String?) : StatCellCaption

    /** A fixed string in place of the name, as the distance cell shows "Auto-paused". */
    data class Fixed(val text: String) : StatCellCaption
}

/**
 * A cell the user can page through. Null on a single-readout cell (the direction cell), which
 * is then neither interactive nor marked with a page indicator — the absence is the whole
 * signal: dots mean "there is more here".
 */
data class StatCellPagingState(
    val page: Int,
    /** Every readout's name, in page order — single source of the dot count and caption. */
    val names: List<String>,
    val onPageChange: (Int) -> Unit,
) {
    val currentName: String get() = names[page]

    fun advance(delta: Int) {
        onPageChange(StatCellPaging.advance(page, names.count(), delta))
    }
}

/**
 * One cell of the 2×2 stats grid: an icon on top, then a value and small caption. A cell with
 * more than one readout (average↔max speed, altitude↔GPS position) is paged by tap or swipe,
 * and says so with a page indicator.
 */
@Composable
fun StatCell(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionContent: StatCellCaption = StatCellCaption.Name,
    /** Icon rotation in degrees — the direction cell points the arrow at the GPS course. */
    iconRotationDegrees: Double = 0.0,
    iconTint: Color = GaugePalette.accent,
    paging: StatCellPagingState? = null,
    /**
     * How far the page indicator sits above the cell's bottom edge. A cell whose bottom edge
     * is covered — the panel's bezel is drawn over its outer 5 dp — passes more, so its dots
     * stand as clear of the bezel as everyone else's stand clear of a bare edge.
     */
    pageIndicatorInset: Dp = DEFAULT_PAGE_INDICATOR_INSET,
) {
    // Unwind the wrapping bearing so the arrow animates the short way past north — the same
    // ContinuousBearing the model layer tests pin. Stateful: each step is measured from the
    // last angle actually shown.
    val unwound = remember { ContinuousBearing() }
    var unwoundDegrees by remember { mutableFloatStateOf(0f) }
    unwoundDegrees = unwound.update(iconRotationDegrees).toFloat()
    val animatedRotation by animateFloatAsState(
        targetValue = unwoundDegrees,
        animationSpec = tween(durationMillis = 300),
        label = "iconRotation",
    )

    val interactive = if (paging != null) {
        Modifier
            .clickable { paging.advance(1) }
            .pointerInput(paging.names) {
                detectHorizontalDragGestures(
                    onDragEnd = {},
                ) { change, dragAmount ->
                    // A decisive horizontal swipe pages; direction from the drag sign.
                    if (abs(dragAmount) > 24) {
                        change.consume()
                        paging.advance(if (dragAmount < 0) 1 else -1)
                    }
                }
            }
    } else {
        Modifier
    }

    Box(modifier = modifier.then(interactive).fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = animatedRotation },
            )

            // A two-line value (the GPS position, the one readout with a newline) shrinks
            // rather than growing the cell.
            Text(
                text = value,
                fontSize = if (value.contains("\n")) 14.sp else 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )

            val captionText = when (captionContent) {
                is StatCellCaption.Name -> paging?.currentName ?: caption ?: ""
                // A space, not "", so an unresolved line still occupies its height.
                is StatCellCaption.Data -> captionContent.text ?: " "
                is StatCellCaption.Fixed -> captionContent.text
            }
            Text(
                text = captionText,
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // Nothing to indicate on a single-readout cell — dots mean "there is more here".
        if (paging != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = pageIndicatorInset),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(paging.names.count()) { index ->
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                Color.White.copy(alpha = if (index == paging.page) 0.85f else 0.25f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

val DEFAULT_PAGE_INDICATOR_INSET: Dp = 8.dp
