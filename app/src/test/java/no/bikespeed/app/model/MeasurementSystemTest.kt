package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

/**
 * The model layer is SI throughout and only the view layer converts, so these conversions sit
 * on the boundary where a mistake would be visible on the gauge but invisible in the data.
 */
class MeasurementSystemTest {

    @Test
    fun `metric converts meters per second to kilometers per hour`() {
        val cases = listOf(0.0 to 0.0, 8.0 to 28.8, 10.0 to 36.0, 27.7778 to 100.0)
        for ((mps, kmh) in cases) {
            assertThat(MeasurementSystem.METRIC.speedValue(mps)).isWithin(0.01).of(kmh)
        }
    }

    @Test
    fun `imperial converts meters per second to miles per hour`() {
        val cases = listOf(0.0 to 0.0, 8.0 to 17.8956, 26.8224 to 60.0)
        for ((mps, mph) in cases) {
            assertThat(MeasurementSystem.IMPERIAL.speedValue(mps)).isWithin(0.01).of(mph)
        }
    }

    /**
     * The gauge maximum is stored canonically in km/h no matter which unit is on screen, so
     * switching units must not drift the stored value. The Settings stepper round-trips
     * through this on every tap.
     */
    @Test
    fun `the gauge maximum round-trips through its display unit`() {
        for (system in MeasurementSystem.entries) {
            val canonical = 60.0

            val displayed = system.maxGaugeSpeedValue(fromCanonicalKmh = canonical)
            val restored = system.canonicalKmh(fromMaxGaugeSpeedValue = displayed)

            assertThat(restored).isWithin(0.001).of(canonical)
        }
    }

    /**
     * Platform formatters render this unit as "km/h" everywhere, but Norwegian riders expect
     * "km/t" — hence the hand-picked labels, pinned to match the iOS gauge exactly.
     */
    @Test
    fun `the gauge unit label uses the abbreviation riders expect`() {
        assertThat(MeasurementSystem.METRIC.gaugeUnitLabel(Locale.forLanguageTag("en"))).isEqualTo("KM/H")
        assertThat(MeasurementSystem.METRIC.gaugeUnitLabel(Locale.forLanguageTag("nb"))).isEqualTo("KM/T")
        assertThat(MeasurementSystem.IMPERIAL.gaugeUnitLabel(Locale.forLanguageTag("en"))).isEqualTo("MPH")
        assertThat(MeasurementSystem.IMPERIAL.gaugeUnitLabel(Locale.forLanguageTag("nb"))).isEqualTo("MPH")
    }

    /** Norwegian formats decimals with a comma — the formatted speed must follow the locale. */
    @Test
    fun `formatted speed follows the locale's decimal separator`() {
        val en = MeasurementSystem.METRIC.formattedSpeed(8.0, fractionDigits = 1, locale = Locale.forLanguageTag("en"))
        val nb = MeasurementSystem.METRIC.formattedSpeed(8.0, fractionDigits = 1, locale = Locale.forLanguageTag("nb"))

        assertThat(en).isEqualTo("28.8 km/h")
        assertThat(nb).isEqualTo("28,8 km/t")
    }
}
