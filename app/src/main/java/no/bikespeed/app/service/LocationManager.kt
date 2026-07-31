package no.bikespeed.app.service

import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.bikespeed.app.model.CircularMean
import no.bikespeed.app.model.Coordinate
import no.bikespeed.app.model.GpsSignalQuality
import no.bikespeed.app.model.LocationFix

/**
 * The GPS filter pipeline: publishes filtered/smoothed speed, course, altitude, and position.
 * All published values are in SI units (m/s, meters, degrees) — unit conversion happens only
 * in views.
 *
 * Platform-free on purpose: the fused-location and sensor glue (the platform layer) feeds
 * [process] and [processHeading]; every rule lives here where plain JVM tests can drive it.
 * Implements [LocationSource] — accepted fixes fan out synchronously to listeners, so
 * `TripManager.consume` runs inside [process] and a test can move the rider and assert
 * distance on the next line.
 */
class LocationManager(
    private val now: NowProvider = NowProvider { Instant.now() },
) : LocationSource {

    private val _rawSpeed = MutableStateFlow(0.0)
    private val _displaySpeed = MutableStateFlow(0.0)
    private val _course = MutableStateFlow<Double?>(null)
    private val _heading = MutableStateFlow<Double?>(null)
    private val _isCourseLive = MutableStateFlow(false)
    private val _altitude = MutableStateFlow<Double?>(null)
    private val _coordinate = MutableStateFlow<Coordinate?>(null)
    private val _hasFix = MutableStateFlow(false)
    private val _signalQuality = MutableStateFlow(GpsSignalQuality.POOR)

    val rawSpeedFlow: StateFlow<Double> = _rawSpeed.asStateFlow()
    val displaySpeedFlow: StateFlow<Double> = _displaySpeed.asStateFlow()
    val courseFlow: StateFlow<Double?> = _course.asStateFlow()
    val headingFlow: StateFlow<Double?> = _heading.asStateFlow()
    val isCourseLiveFlow: StateFlow<Boolean> = _isCourseLive.asStateFlow()
    val altitudeFlow: StateFlow<Double?> = _altitude.asStateFlow()
    val coordinateFlow: StateFlow<Coordinate?> = _coordinate.asStateFlow()
    val hasFixFlow: StateFlow<Boolean> = _hasFix.asStateFlow()
    val signalQualityFlow: StateFlow<GpsSignalQuality> = _signalQuality.asStateFlow()

    /** m/s, deadbanded. */
    val rawSpeed: Double get() = _rawSpeed.value

    /** m/s, EMA-smoothed — what the gauge needle draws. */
    val displaySpeed: Double get() = _displaySpeed.value

    /**
     * Degrees, STICKY: holds its last valid value at a standstill rather than nilling, so
     * `course ?: heading` alone could never fall back to the compass — that's what
     * [isCourseLive] is for.
     */
    val course: Double? get() = _course.value

    /**
     * The magnetometer, smoothed circularly (see [CircularMean]). Null where there is no
     * magnetometer and until the first usable reading arrives.
     */
    val heading: Double? get() = _heading.value

    /** Whether [course] is currently refreshed by fixes (rider moving fast enough for GPS course to mean anything). */
    val isCourseLive: Boolean get() = _isCourseLive.value

    val altitude: Double? get() = _altitude.value
    val coordinate: Coordinate? get() = _coordinate.value
    val hasFix: Boolean get() = _hasFix.value
    val signalQuality: GpsSignalQuality get() = _signalQuality.value

    /**
     * **The direction the rider is actually travelling** — not the same question either
     * sensor answers alone.
     *
     * Moving: GPS course, the true travel direction and, unlike the compass, immune to how
     * the phone is clamped to the bars. Below [COURSE_SPEED_THRESHOLD] course is noise and
     * held sticky, so the arrow would go stale at every red light; the magnetometer fills
     * that gap (the way the bike *points* is the best guess at where it's about to go).
     * Falls back to the last course where there's no magnetometer.
     */
    val travelDirection: Double?
        get() = if (isCourseLive) course else (heading ?: course)

    private val listeners = mutableListOf<FixListener>()

    /**
     * Smoothed on the unit circle, not in degrees — a plain average of 359° and 1° is due
     * *south* (see [CircularMean]).
     */
    private val headingSmoother = CircularMean(smoothingFactor = 0.25)

    /**
     * Set by the platform layer to actually start/stop background recording (the foreground
     * service + fused-request adjustments). The pure pipeline only forwards the request.
     */
    var backgroundUpdatesHandler: ((Boolean) -> Unit)? = null

    override fun addFixListener(listener: FixListener) {
        listeners.add(listener)
    }

    override fun setBackgroundUpdates(enabled: Boolean) {
        backgroundUpdatesHandler?.invoke(enabled)
    }

    /** All filtering lives here; the platform callback only hands its fix over. */
    fun process(fix: LocationFix) {
        // A cached fix reports where the phone *was*; drop it before it poses as current.
        // Ahead of everything and without touching hasFix — a stale fix isn't a
        // signal-quality problem, and letting it drive the indicator would flash the state
        // of a fix we're about to ignore. (The platform replays the last known location
        // immediately on subscribing, which can be minutes old with excellent accuracy.)
        val ageSeconds = (now.now().toEpochMilli() - fix.timestamp.toEpochMilli()) / 1000.0
        if (ageSeconds > MAX_FIX_AGE_SECONDS) return

        _signalQuality.value = GpsSignalQuality.fromHorizontalAccuracy(
            horizontalAccuracy = fix.horizontalAccuracy,
            goodWithin = GOOD_HORIZONTAL_ACCURACY,
            acceptableWithin = MAX_HORIZONTAL_ACCURACY,
        )

        // Poor signal: freeze speed/course/altitude/position at their last trusted values and
        // don't emit, so TripManager adds nothing to distance either.
        if (!signalQuality.isUsable) {
            _hasFix.value = false
            return
        }
        _hasFix.value = true

        // GPS speed doesn't settle at zero when the bike does — it wanders a few tenths of a
        // m/s, drawn as the needle creeping to 1–2 km/h and back while parked; smoothing
        // can't help, the noise is in the input. speedAccuracy is the fix's own error bar on
        // that speed, so a reading inside it is indistinguishable from standstill, and the
        // deadband widens exactly when the fix turns noisy (indoors, street canyon) instead
        // of one fixed width. Bounded both ends: never below the floor (a fix without one
        // falls back there), never above COURSE_SPEED_THRESHOLD (the GPS-is-noise line), so a
        // pessimistic error bar can't swallow a real ride. A null speed ("unknown") reads as
        // standing still too.
        val speedNoiseFloor = min(
            max(fix.speedAccuracy ?: STANDSTILL_SPEED_FLOOR, STANDSTILL_SPEED_FLOOR),
            COURSE_SPEED_THRESHOLD,
        )
        val speed = fix.speed
        _rawSpeed.value = if (speed == null || speed <= speedNoiseFloor) 0.0 else speed
        _displaySpeed.value = SMOOTHING_FACTOR * rawSpeed + (1 - SMOOTHING_FACTOR) * displaySpeed

        // A null course accuracy is "unknown" and accepted (matching the platform sentinel
        // semantics); a wide one is rejected.
        val courseAccuracy = fix.courseAccuracy
        val courseIsReliable = fix.course != null &&
            (courseAccuracy == null || courseAccuracy <= MAX_COURSE_ACCURACY)
        _isCourseLive.value = courseIsReliable && rawSpeed >= COURSE_SPEED_THRESHOLD
        if (isCourseLive) {
            _course.value = fix.course
        }
        // else: hold course at its last valid value. isCourseLive records it's now stale, so
        // travelDirection knows to ask the compass instead.

        fix.usableAltitude()?.let { _altitude.value = it }

        _coordinate.value = Coordinate(fix.latitude, fix.longitude)
        listeners.forEach { it.onFix(fix) }
    }

    /**
     * Folds a compass reading into the smoothed [heading]. Takes plain degrees so the logic
     * is drivable without platform types.
     *
     * [trueHeading] (geographic north) is what we want, but the platform reports it negative
     * until a location fix resolves magnetic declination — exactly when the rider first opens
     * the app at a standstill. Magnetic north is a few degrees off and good enough to point
     * an arrow.
     */
    fun processHeading(trueHeading: Double, magneticHeading: Double, accuracy: Double) {
        // Negative accuracy = unusable: the magnetometer is being interfered with, not rare
        // near a bike frame and a phone speaker.
        if (accuracy < 0) return

        val bearing = if (trueHeading >= 0) trueHeading else magneticHeading
        if (bearing < 0) return

        _heading.value = headingSmoother.add(bearing)
    }

    companion object {
        const val SMOOTHING_FACTOR: Double = 0.35

        /** "Good" (green) band ceiling; up to [MAX_HORIZONTAL_ACCURACY] is "fair" (yellow). Worse is rejected. */
        const val GOOD_HORIZONTAL_ACCURACY: Double = 10.0
        const val MAX_HORIZONTAL_ACCURACY: Double = 30.0

        /** m/s (~3.6 km/h) below which GPS course is noise. */
        const val COURSE_SPEED_THRESHOLD: Double = 1.0

        /**
         * Floor for the standstill deadband, and the width for fixes reporting no speed
         * accuracy. ~1.8 km/h: below that you're pushing the bike, not riding it.
         */
        const val STANDSTILL_SPEED_FLOOR: Double = 0.5

        const val MAX_COURSE_ACCURACY: Double = 90.0

        /**
         * How old a fix may be and still count as current. Filtered separately from accuracy
         * because subscribing replays the last cached fix immediately — minutes old yet with
         * excellent accuracy, so the accuracy filter waves it through. Downstream reads a
         * fix's timestamp as "now".
         */
        const val MAX_FIX_AGE_SECONDS: Double = 5.0
    }
}
