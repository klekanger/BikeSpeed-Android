package no.bikespeed.app.ui.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import no.bikespeed.app.ui.theme.BikeSpeedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The page indicator must never draw over the readout. The regression this pins is the GPS
 * position page on a 1080 × 1920 phone: its two-line value pushed the "Position" caption down
 * into the dots, which used to be overlaid on the cell's bottom edge rather than given room.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatCellTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a two-line value keeps its caption clear of the page indicator`() {
        compose.setContent {
            // A bottom-row cell of a 1080 × 1920 (411 × 731 dp) phone. Inside the app theme, whose
            // 24 sp body line height is what set the two lines so far apart on the device.
            BikeSpeedTheme {
            Box(modifier = Modifier.size(width = 180.dp, height = 125.dp)) {
                StatCell(
                    icon = Icons.Filled.MyLocation,
                    value = "59.97827° N\n10.76557° E",
                    paging = StatCellPagingState(
                        page = 3,
                        names = listOf("Altitude", "Climb", "Descent", "Position"),
                        onPageChange = {},
                    ),
                    pageIndicatorInset = DEFAULT_PAGE_INDICATOR_INSET + 5.dp,
                )
            }
            }
        }

        val caption = compose.onNodeWithTag("statCellCaption", useUnmergedTree = true).getBoundsInRoot()
        val indicator = compose.onNodeWithTag("statCellPageIndicator", useUnmergedTree = true).getBoundsInRoot()
        // A whole line of the 12 sp caption — a caption squeezed by the value above it keeps
        // its place but loses its height, and draws clipped or not at all.
        assertThat(caption.bottom - caption.top).isAtLeast(14.dp)
        assertThat(caption.bottom).isAtMost(indicator.top)
    }
}
