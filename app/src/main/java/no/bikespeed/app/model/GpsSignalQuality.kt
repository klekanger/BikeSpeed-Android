package no.bikespeed.app.model

/**
 * Coarse GPS signal quality derived from a fix's horizontal accuracy, used to drive the
 * signal indicator. [POOR] means the fix is too inaccurate to trust, so `LocationManager`
 * freezes speed and stops accumulating distance rather than feeding it in.
 */
enum class GpsSignalQuality {
    GOOD,
    FAIR,
    POOR;

    /** Whether a fix at this quality should be trusted enough to update speed / accumulate distance. */
    val isUsable: Boolean get() = this != POOR

    companion object {
        /**
         * Buckets a horizontal accuracy (meters; negative means "invalid") into a quality level.
         * [goodWithin] and [acceptableWithin] are the upper accuracy bounds for the green and
         * yellow bands; anything worse than [acceptableWithin], or invalid, is [POOR].
         */
        fun fromHorizontalAccuracy(
            horizontalAccuracy: Double,
            goodWithin: Double,
            acceptableWithin: Double,
        ): GpsSignalQuality = when {
            horizontalAccuracy < 0 || horizontalAccuracy > acceptableWithin -> POOR
            horizontalAccuracy <= goodWithin -> GOOD
            else -> FAIR
        }
    }
}
