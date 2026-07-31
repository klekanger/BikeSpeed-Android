package no.bikespeed.app.platform

import android.content.Context
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class PressureAltitudeTest {

    @Test
    fun `standard atmosphere reads zero meters`() {
        assertThat(PressureAltitude.metersFromPressure(1013.25)).isWithin(0.001).of(0.0)
    }

    /** A golden value from the barometric formula: 900 hPa is roughly 989 m. */
    @Test
    fun `900 hPa is roughly a thousand meters up`() {
        assertThat(PressureAltitude.metersFromPressure(900.0)).isWithin(2.0).of(989.0)
    }

    @Test
    fun `lower pressure means higher altitude`() {
        assertThat(PressureAltitude.metersFromPressure(950.0))
            .isLessThan(PressureAltitude.metersFromPressure(900.0))
    }
}

/**
 * The relative-altitude contract TripManager depends on: the zero point is the first reading
 * after start, and nothing from before a stop survives it.
 */
@RunWith(RobolectricTestRunner::class)
class BarometerAltitudeSourceTest {

    private fun source(): BarometerAltitudeSource {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return BarometerAltitudeSource(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    }

    @Test
    fun `the first reading becomes the zero point`() {
        val barometer = source()

        barometer.onPressureReading(1000.0)

        assertThat(barometer.relativeAltitude!!).isWithin(0.001).of(0.0)
    }

    @Test
    fun `later readings report their offset from the baseline`() {
        val barometer = source()
        barometer.onPressureReading(1000.0)

        barometer.onPressureReading(999.0) // less pressure: higher up

        val relative = barometer.relativeAltitude!!
        assertThat(relative).isGreaterThan(0.0)
        assertThat(relative).isWithin(1.0).of(8.5) // ~8.4 m per hPa near sea level
    }

    @Test
    fun `stopping clears the reading so a stale value can never be read after a restart`() {
        val barometer = source()
        barometer.onPressureReading(1000.0)
        barometer.onPressureReading(995.0)

        barometer.stopUpdates()

        assertThat(barometer.relativeAltitude).isNull()

        // And the next reading after a restart re-bases at zero rather than continuing the
        // old baseline.
        barometer.onPressureReading(990.0)
        assertThat(barometer.relativeAltitude!!).isWithin(0.001).of(0.0)
    }
}
