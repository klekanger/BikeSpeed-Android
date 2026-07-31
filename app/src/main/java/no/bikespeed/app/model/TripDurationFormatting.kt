package no.bikespeed.app.model

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Formats a trip's active duration. Independent of metric/imperial, so it lives here rather
 * than on [MeasurementSystem]. Matches the iOS pattern: "h:mm:ss" from one hour up, "m:ss"
 * below it.
 */
object TripDurationFormatting {
    fun formatted(seconds: Double, locale: Locale): String {
        val total = seconds.roundToLong()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (total >= 3600) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(locale, "%d:%02d", minutes, secs)
        }
    }
}
