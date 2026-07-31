package no.bikespeed.app.model

import java.text.NumberFormat
import java.util.Locale

/**
 * Metric vs. imperial display units. The model layer is SI throughout (m, m/s);
 * only the UI converts, via this type.
 *
 * Unlike iOS (which leans on Foundation's `Measurement`), conversions here use explicit
 * constants and unit labels are hand-picked — pinned by tests instead of by platform
 * formatters.
 */
enum class MeasurementSystem {
    METRIC,
    IMPERIAL;

    /**
     * Center-of-gauge unit label, e.g. "KM/H" / "KM/T" / "MPH".
     *
     * Hardcoded rather than platform-formatted: ICU yields "km/h" but Norwegian riders
     * expect "km/t", and the iOS app pins exactly these labels.
     */
    fun gaugeUnitLabel(locale: Locale): String = when (this) {
        METRIC -> if (locale.language == "nb") "KM/T" else "KM/H"
        IMPERIAL -> "MPH"
    }

    /** Converts m/s into this system's display speed unit (km/h or mph). */
    fun speedValue(metersPerSecond: Double): Double = when (this) {
        METRIC -> metersPerSecond * KMH_PER_MPS
        IMPERIAL -> metersPerSecond * MPH_PER_MPS
    }

    /** Converts meters into this system's display distance unit (km or mi). */
    fun distanceValue(meters: Double): Double = when (this) {
        METRIC -> meters / 1000.0
        IMPERIAL -> meters / METERS_PER_MILE
    }

    /** Converts meters into this system's display altitude unit (m or ft). */
    fun altitudeValue(meters: Double): Double = when (this) {
        METRIC -> meters
        IMPERIAL -> meters * FEET_PER_METER
    }

    val distanceUnitLabel: String get() = when (this) {
        METRIC -> "km"
        IMPERIAL -> "mi"
    }

    val altitudeUnitLabel: String get() = when (this) {
        METRIC -> "m"
        IMPERIAL -> "ft"
    }

    /** Formats speed as "<value> <unit>", e.g. "24.3 km/h" / "24,3 km/t" / "15.1 mph". */
    fun formattedSpeed(metersPerSecond: Double, fractionDigits: Int = 1, locale: Locale): String =
        formattedSpeedValue(speedValue(metersPerSecond), fractionDigits, locale)

    fun formattedDistance(meters: Double, locale: Locale): String =
        "${number(distanceValue(meters), fractionDigits = 2, locale = locale)} $distanceUnitLabel"

    fun formattedAltitude(meters: Double, locale: Locale): String =
        "${number(altitudeValue(meters), fractionDigits = 0, locale = locale)} $altitudeUnitLabel"

    /** Converts a gauge max speed stored canonically in km/h into this system's speed unit. */
    fun maxGaugeSpeedValue(fromCanonicalKmh: Double): Double = when (this) {
        METRIC -> fromCanonicalKmh
        IMPERIAL -> fromCanonicalKmh * MPH_PER_KMH
    }

    /** Converts a gauge max speed expressed in this system's speed unit back into canonical km/h. */
    fun canonicalKmh(fromMaxGaugeSpeedValue: Double): Double = when (this) {
        METRIC -> fromMaxGaugeSpeedValue
        IMPERIAL -> fromMaxGaugeSpeedValue / MPH_PER_KMH
    }

    /** Formats a gauge max speed stored canonically in km/h in this system's speed unit. */
    fun formattedMaxGaugeSpeed(fromCanonicalKmh: Double, fractionDigits: Int = 0, locale: Locale): String =
        formattedSpeedValue(maxGaugeSpeedValue(fromCanonicalKmh), fractionDigits, locale)

    /** Stepper increment for the gauge max speed, expressed in this system's speed unit. */
    val maxGaugeSpeedStep: Double get() = 5.0

    /** Stepper range for the gauge max speed, expressed in this system's speed unit. */
    val maxGaugeSpeedRange: ClosedFloatingPointRange<Double> get() = when (this) {
        METRIC -> 20.0..200.0
        IMPERIAL -> 10.0..125.0
    }

    private fun formattedSpeedValue(value: Double, fractionDigits: Int, locale: Locale): String =
        "${number(value, fractionDigits, locale)} ${gaugeUnitLabel(locale).lowercase(locale)}"

    private fun number(value: Double, fractionDigits: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }.format(value)

    companion object {
        const val KMH_PER_MPS = 3.6
        const val METERS_PER_MILE = 1609.344
        const val MPH_PER_MPS = 3600.0 / METERS_PER_MILE // 2.2369362920544…
        const val MPH_PER_KMH = 1000.0 / METERS_PER_MILE
        const val FEET_PER_METER = 1.0 / 0.3048 // 3.280839895…
    }
}
