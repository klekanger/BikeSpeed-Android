package no.bikespeed.app.model

/**
 * 8-point compass direction derived from a GPS course (degrees clockwise from true north).
 *
 * [abbreviationKey] is the English abbreviation, which doubles as the localization key —
 * Norwegian differs on the east/west axis (Ø/V replace E/W), resolved via string resources
 * when the localization step lands.
 */
enum class CompassDirection(val abbreviationKey: String) {
    N("N"), NE("NE"), E("E"), SE("SE"), S("S"), SW("SW"), W("W"), NW("NW");

    companion object {
        fun fromCourse(course: Double): CompassDirection {
            val normalized = course % 360.0
            val positive = if (normalized < 0) normalized + 360.0 else normalized
            val index = (((positive + 22.5) % 360.0) / 45.0).toInt()
            return entries[index]
        }
    }
}
