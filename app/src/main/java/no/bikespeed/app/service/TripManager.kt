package no.bikespeed.app.service

import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import no.bikespeed.app.model.AltitudeSample
import no.bikespeed.app.model.ElevationAccumulator
import no.bikespeed.app.model.GradeCalculator
import no.bikespeed.app.model.LocationFix
import no.bikespeed.app.model.RouteSample
import no.bikespeed.app.model.TripLogEntry
import no.bikespeed.app.model.TripState

/**
 * Owns the Start/Pause/Reset trip state machine and the distance/average-speed accumulation
 * derived from accepted location fixes. Elapsed active time is wall-clock based so it stays
 * correct across app backgrounding.
 *
 * All observable values are [StateFlow]s whose `.value` writes are synchronous — a test (or
 * the auto-pause logic) reads the new value on the very next line, matching the iOS
 * Observation semantics. The 0.5 s production driver for [tick] lives in the UI layer;
 * tests call [tick] directly after advancing the injected clock.
 */
class TripManager(
    private val locationSource: LocationSource,
    private val altimeter: AltitudeSource,
    private val settings: SettingsStore,
    private val now: NowProvider,
) {

    private val _state = MutableStateFlow(TripState.IDLE)
    private val _accumulatedDistance = MutableStateFlow(0.0)
    private val _elapsedActiveDuration = MutableStateFlow(0.0)
    private val _maxSpeed = MutableStateFlow(0.0)

    val stateFlow: StateFlow<TripState> = _state.asStateFlow()
    val accumulatedDistanceFlow: StateFlow<Double> = _accumulatedDistance.asStateFlow()
    val elapsedActiveDurationFlow: StateFlow<Double> = _elapsedActiveDuration.asStateFlow()
    val maxSpeedFlow: StateFlow<Double> = _maxSpeed.asStateFlow()

    val state: TripState get() = _state.value

    /** Meters. */
    val accumulatedDistance: Double get() = _accumulatedDistance.value

    /** Seconds of active riding — frozen while paused or auto-paused. */
    val elapsedActiveDuration: Double get() = _elapsedActiveDuration.value

    /** m/s, instantaneous peak. */
    val maxSpeed: Double get() = _maxSpeed.value

    private val _isAutoPaused = MutableStateFlow(false)

    val isAutoPausedFlow: StateFlow<Boolean> = _isAutoPaused.asStateFlow()

    /**
     * A running trip stopped accumulating because the rider is standing still. Deliberately a
     * flag on RUNNING, not a [TripState] case: a manual pause must stay distinguishable from
     * an auto-pause (else rolling forward resumes a deliberately-paused trip), and
     * [canSaveTrip] must not light up at every red light.
     */
    val isAutoPaused: Boolean get() = _isAutoPaused.value

    private val _totalAscent = MutableStateFlow<Double?>(null)
    private val _totalDescent = MutableStateFlow<Double?>(null)
    private val _currentGrade = MutableStateFlow<Double?>(null)

    val totalAscentFlow: StateFlow<Double?> = _totalAscent.asStateFlow()
    val totalDescentFlow: StateFlow<Double?> = _totalDescent.asStateFlow()
    val currentGradeFlow: StateFlow<Double?> = _currentGrade.asStateFlow()

    /**
     * Null until any usable altitude has been sampled: "no data" must stay distinguishable
     * from "a flat ride" in the live UI just as it is in the saved log (see [TripLogEntry]).
     */
    val totalAscent: Double? get() = _totalAscent.value

    /** Meters, positive. */
    val totalDescent: Double? get() = _totalDescent.value

    /**
     * Rise over a trailing ~30 m of run — e.g. 0.05 for a 5 % climb. Null until enough of the
     * trip has been ridden to measure one honestly; see [GradeCalculator].
     */
    val currentGrade: Double? get() = _currentGrade.value

    /**
     * The trip's height profile, for the saved trip's elevation chart. A plain list, not a
     * flow — nothing draws it live; it is read once, at Save.
     */
    val altitudeProfile: List<AltitudeSample> get() = mutableAltitudeProfile

    /**
     * The trip's recorded track, for the detail map and GPX export. Decimated by distance
     * like the profile but far more finely: a track sampled every 25 m visibly cuts corners
     * on a map. Also read once, at Save.
     */
    val routeSamples: List<RouteSample> get() = mutableRouteSamples

    private val mutableAltitudeProfile = mutableListOf<AltitudeSample>()
    private var lastAltitudeSampleDistance = 0.0
    private val mutableRouteSamples = mutableListOf<RouteSample>()
    private var lastRouteSampleDistance = 0.0

    /**
     * The altitude source this trip is committed to, latched once from data actually arriving
     * (see [latchElevationSource]): hardware presence can't decide — a barometer without its
     * permission reports available yet never delivers — and the two sources measure against
     * different zero points, so switching mid-trip would bank the difference as phantom climb.
     */
    private enum class ElevationSource { UNDECIDED, BAROMETER, GPS }

    private var elevationSource = ElevationSource.UNDECIDED

    /**
     * Starts on the GPS deadband as a placeholder; the latch replaces it with an accumulator
     * sized to the source that actually won.
     */
    private var elevation = ElevationAccumulator(deadband = GPS_ALTITUDE_DEADBAND)
    private var grade = GradeCalculator(windowDistance = GRADE_WINDOW_METERS)
    private var lastElevationSampleTimestamp: Instant? = null
    private var barometerGraceFixesRemaining = BAROMETER_LATCH_GRACE_FIXES

    private var accumulatedActiveDuration = 0.0
    private var activeStart: Instant? = null
    private var previousFix: LocationFix? = null

    /**
     * Set once in [start] and never touched by [resume], unlike [activeStart] — this is the
     * stable wall-clock start time saved into a trip log entry.
     */
    private var tripStartDate: Instant? = null

    /**
     * How many implausible fixes in a row to reject before concluding the anchor is the bad
     * one. The guard can't tell which of the two locations is the artifact, so it blames the
     * new fix — correct for an isolated spike. But if [previousFix] is the outlier, every
     * later fix measures an implausible speed against it and is dropped, and a dropped fix
     * doesn't advance the anchor, so distance would freeze for the rest of the ride (only
     * Reset clears it). A genuine spike is a single fix, so a streak means the anchor is
     * wrong: re-anchor and write off the gap, costing at most this many fixes instead of the
     * whole trip.
     */
    private var teleportRejections = 0

    private var lastAcceptedFix: Instant? = null

    /** Arms the auto-pause debounce; runs on the GPS clock (fix timestamps). */
    private var belowThresholdSince: Instant? = null

    /** m/s. */
    val averageSpeed: Double
        get() {
            val duration = currentElapsedActiveDuration()
            if (duration <= 0) return 0.0
            return accumulatedDistance / duration
        }

    /**
     * Whether saving would currently succeed — gates the Save button so it's enabled only
     * when there's something meaningful to save.
     */
    val canSaveTrip: Boolean
        get() = state == TripState.PAUSED &&
            accumulatedActiveDuration >= SAVE_MIN_DURATION_SECONDS &&
            accumulatedDistance >= SAVE_MIN_DISTANCE_METERS

    /**
     * Whether [reset] would clear anything — gates the Reset button. Resettable exactly while
     * manually paused: mid-ride (including auto-pause) it must not be wiped from under the
     * rider, and an idle trip is already at zero. No distance/duration floor, unlike
     * [canSaveTrip]: clearing a false start too short to save is precisely what Reset is for.
     */
    val canResetTrip: Boolean
        get() = state == TripState.PAUSED

    init {
        locationSource.addFixListener { consume(it) }
    }

    /**
     * The periodic work the trip clock drives: retire an unconfirmed auto-pause and republish
     * elapsed time so a running trip's duration ticks up between fixes. Driven by a 0.5 s
     * loop in production; tests drive it directly after advancing the clock.
     */
    fun tick() {
        // Switching auto-pause off must release one already active, and the clock is the only
        // place that can see it: updateAutoPause() reads the setting too but runs on accepted
        // fixes, and an auto-paused rider is standing still, so there may be no next fix. At
        // 0.5 s a toggle still feels instant.
        if (!settings.autoPauseEnabled) clearAutoPause()
        releaseAutoPauseIfUnconfirmed()
        if (activeStart == null) return
        _elapsedActiveDuration.value = currentElapsedActiveDuration()
    }

    fun start() {
        if (state != TripState.IDLE) return
        _state.value = TripState.RUNNING
        startClock()
        tripStartDate = now.now()
        locationSource.setBackgroundUpdates(true)
        altimeter.startUpdates()
    }

    fun pause() {
        if (state != TripState.RUNNING) return
        // Set before clearAutoPause() so it sees PAUSED and doesn't restart the clock: a
        // manual pause supersedes an auto-pause, and releasing it is now the user's call.
        _state.value = TripState.PAUSED
        suspendClock()
        clearAutoPause()
        locationSource.setBackgroundUpdates(false)
        altimeter.stopUpdates()
        // A parked bike isn't on a slope; otherwise the readout asserts the last hill for the
        // whole pause.
        _currentGrade.value = null
    }

    fun resume() {
        if (state != TripState.PAUSED) return
        startClock()
        _state.value = TripState.RUNNING
        locationSource.setBackgroundUpdates(true)
        altimeter.startUpdates()
        // Altitude across the pause wasn't ridden, and the restarted barometer rebases its
        // zero anyway, so re-baseline rather than bank the difference as climb. (The
        // sampling-gap rule in sampleElevation catches this too, but a short pause with a
        // rebased barometer would slip under it.)
        elevation.reanchor()
        grade.reset()
    }

    fun reset() {
        if (state != TripState.IDLE && state != TripState.PAUSED) return
        _accumulatedDistance.value = 0.0
        accumulatedActiveDuration = 0.0
        _elapsedActiveDuration.value = 0.0
        _maxSpeed.value = 0.0
        activeStart = null
        previousFix = null
        teleportRejections = 0
        tripStartDate = null
        clearAutoPause() // safe here: state is never RUNNING, so this can't start the clock
        lastAcceptedFix = null
        mutableAltitudeProfile.clear()
        lastAltitudeSampleDistance = 0.0
        mutableRouteSamples.clear()
        lastRouteSampleDistance = 0.0
        elevation.reset()
        grade.reset()
        _totalAscent.value = null
        _totalDescent.value = null
        _currentGrade.value = null
        elevationSource = ElevationSource.UNDECIDED
        barometerGraceFixesRemaining = BAROMETER_LATCH_GRACE_FIXES
        lastElevationSampleTimestamp = null
        _state.value = TripState.IDLE
        locationSource.setBackgroundUpdates(false)
        altimeter.stopUpdates()
    }

    /**
     * Snapshots the current trip into a saveable log entry, or null if there's no paused trip
     * or it's too short/short-lived to be meaningful.
     *
     * The entry is the trip's *scalars* only; its height profile and track are separate
     * payloads ([altitudeProfile], [routeSamples]) the caller passes to the data stack
     * alongside this. See [TripLogEntry] for why they aren't in it.
     */
    fun makeLogEntry(): TripLogEntry? {
        if (state != TripState.PAUSED) return null
        val tripStartDate = tripStartDate ?: return null
        if (accumulatedActiveDuration < SAVE_MIN_DURATION_SECONDS) return null
        if (accumulatedDistance < SAVE_MIN_DISTANCE_METERS) return null
        return TripLogEntry(
            id = UUID.randomUUID(),
            startDate = tripStartDate,
            duration = accumulatedActiveDuration,
            distance = accumulatedDistance,
            averageSpeed = averageSpeed,
            maxSpeed = maxSpeed,
            // Already null when no fix ever carried a usable altitude: "we don't know" must
            // stay distinguishable from "it was flat". See TripLogEntry.
            totalAscent = totalAscent,
            totalDescent = totalDescent,
        )
    }

    /**
     * [activeStart] is non-null exactly while the clock runs — not idle, not manually or
     * auto-paused — so these two are the only places the wall-clock total is folded up,
     * shared by both pauses.
     */
    private fun startClock() {
        activeStart = now.now()
    }

    private fun suspendClock() {
        val activeStart = this.activeStart ?: return // already stopped; folding again would double-count
        accumulatedActiveDuration += now.now().secondsSince(activeStart)
        this.activeStart = null
        _elapsedActiveDuration.value = accumulatedActiveDuration
    }

    private fun currentElapsedActiveDuration(): Double =
        accumulatedActiveDuration + (activeStart?.let { now.now().secondsSince(it) } ?: 0.0)

    private fun beginAutoPause() {
        _isAutoPaused.value = true
        suspendClock()
        // Standing still: no current slope, else the readout keeps asserting the last hill.
        _currentGrade.value = null
    }

    /**
     * The one place auto-pause is unwound. It is two pieces of state — the [isAutoPaused]
     * flag and the [belowThresholdSince] debounce that arms it — and every exit clears both
     * here: manual pause, reset, setting off, a resume-speed fix, the fix-loss watchdog, and
     * moving again. Owning both together is the point.
     */
    private fun clearAutoPause() {
        belowThresholdSince = null
        if (!isAutoPaused) return
        _isAutoPaused.value = false
        // Guarded, so clearing an auto-pause can never restart the clock on a manually
        // paused trip.
        if (state == TripState.RUNNING) startClock()
    }

    /**
     * Auto-pause is only ever entered on positive evidence — an accepted fix below the
     * threshold — so it must be released once that evidence stops. The location filter drops
     * unusable-accuracy fixes, and accuracy often degrades *because* the phone is still, so
     * without this a rider who auto-paused at a light and rode off through poor signal would
     * never see a resume-speed fix: the clock stays frozen and that riding time vanishes.
     * Silence isn't evidence of standing still, so fall back to counting time. If the rider
     * really is stopped, the next usable fix re-arms the debounce and pauses again.
     */
    private fun releaseAutoPauseIfUnconfirmed() {
        if (!isAutoPaused) return
        val lastAcceptedFix = lastAcceptedFix ?: return
        if (now.now().secondsSince(lastAcceptedFix) < AUTO_PAUSE_FIX_TIMEOUT_SECONDS) return
        clearAutoPause()
    }

    /**
     * Decides whether a running trip should stop accumulating. Runs on the GPS clock (the
     * fix's timestamp), not wall time, to match the `dt` used for distance. Safe only because
     * the location filter rejects fixes older than its age gate: a cached fix timestamped
     * minutes ago would otherwise satisfy the debounce on its own and pause a moving rider
     * instantly.
     */
    private fun updateAutoPause(fix: LocationFix) {
        if (!settings.autoPauseEnabled) {
            clearAutoPause()
            return
        }
        // A null speed is "unknown", not a slow one — hold state rather than read it as a
        // standstill.
        val speed = fix.speed ?: return

        if (isAutoPaused) {
            if (speed >= AUTO_RESUME_SPEED) clearAutoPause()
            return
        }

        if (speed >= AUTO_PAUSE_SPEED) {
            clearAutoPause() // moving: disarm the debounce
            return
        }
        val since = belowThresholdSince ?: fix.timestamp
        belowThresholdSince = since
        if (fix.timestamp.secondsSince(since) >= AUTO_PAUSE_DELAY_SECONDS) beginAutoPause()
    }

    /**
     * Moves the point distance is measured from. Clears the rejection streak with it: the
     * streak only ever means "the anchor looks wrong", which a new anchor settles.
     */
    private fun anchor(fix: LocationFix) {
        previousFix = fix
        teleportRejections = 0
    }

    private fun consume(fix: LocationFix) {
        // Wall-clock, not the fix's timestamp: this measures whether usable fixes are still
        // arriving — a fact about *now*, not about when the fix was taken.
        lastAcceptedFix = now.now()

        if (state != TripState.RUNNING) {
            // Keep a fresh anchor while idle/paused so resuming never measures a large
            // gap-distance.
            anchor(fix)
            return
        }

        updateAutoPause(fix)
        if (isAutoPaused) {
            // As above: anchoring every fix keeps the standstill's GPS drift out of the total
            // when the rider pulls away.
            anchor(fix)
            return
        }

        // Top speed from the instantaneous GPS reading (null is "unknown", not slow),
        // clamping teleport spikes with the same ceiling used for distance.
        val speed = fix.speed
        if (speed != null) {
            _maxSpeed.value = max(maxSpeed, min(speed, MAX_PLAUSIBLE_SPEED))
        }

        val previous = previousFix
        if (previous == null) {
            anchor(fix)
            fix.usableAltitude(ceiling = MAX_ALTITUDE_VERTICAL_ACCURACY)?.let { altitude ->
                mutableAltitudeProfile.add(AltitudeSample(distance = 0.0, altitude = altitude))
            }
            appendRouteSample(fix)
            sampleElevation(fix)
            return
        }

        val dt = fix.timestamp.secondsSince(previous.timestamp)
        if (dt <= 0) return

        val delta = fix.distanceTo(previous)
        if (delta / dt > MAX_PLAUSIBLE_SPEED) {
            // Likely a GPS teleport artifact: drop it and hold the anchor, so the next good
            // fix is measured from where the rider actually was. But once spikes pile up the
            // anchor is what's wrong (see teleportRejections) — re-anchor and write off the
            // gap rather than freeze the trip.
            teleportRejections += 1
            if (teleportRejections >= MAX_TELEPORT_REJECTIONS) {
                anchor(fix)
            }
            return
        }

        val advanced = delta > max(JITTER_FLOOR_METERS, 0.5 * fix.horizontalAccuracy)
        if (advanced) {
            _accumulatedDistance.value = accumulatedDistance + delta
        }
        anchor(fix)

        val usableAltitude = fix.usableAltitude(ceiling = MAX_ALTITUDE_VERTICAL_ACCURACY)
        if (usableAltitude != null &&
            accumulatedDistance - lastAltitudeSampleDistance >= ALTITUDE_SAMPLE_DISTANCE_INTERVAL
        ) {
            mutableAltitudeProfile.add(AltitudeSample(distance = accumulatedDistance, altitude = usableAltitude))
            lastAltitudeSampleDistance = accumulatedDistance
        }

        // The isEmpty check isn't redundant with the first-fix branch above: a trip started
        // while the app was already tracking has an anchor from before start(), so its first
        // *running* fix comes through here — without this the track would begin 10 m in,
        // planting the start marker down the road.
        if (mutableRouteSamples.isEmpty() ||
            accumulatedDistance - lastRouteSampleDistance >= ROUTE_SAMPLE_DISTANCE_INTERVAL
        ) {
            appendRouteSample(fix)
        }

        // Only a fix that moved the trip feeds elevation — the same jitter-floor protection
        // distance gets, applied to climb: GPS altitude wander and barometric drift at a red
        // light must not accumulate, and auto-pause can't guarantee that (the rider may
        // switch it off).
        if (advanced) {
            sampleElevation(fix)
        }
    }

    /**
     * Records where the rider is, for the track. Unlike elevation sampling, this takes the
     * fix with or without a usable altitude: a point with no height is still a point on the
     * map, and GPX just omits its `<ele>`.
     */
    private fun appendRouteSample(fix: LocationFix) {
        mutableRouteSamples.add(
            RouteSample(
                latitude = fix.latitude,
                longitude = fix.longitude,
                altitude = fix.usableAltitude(ceiling = MAX_ALTITUDE_VERTICAL_ACCURACY),
                timestamp = fix.timestamp,
                // Null is "unknown" — store null, not a bogus reading, so the profile derives
                // speed from the timestamps for that point instead. Clamp the rest to the
                // same ceiling maxSpeed uses, so a glitchy fix can't plant a spike in the
                // recorded track.
                speed = fix.speed?.let { min(it, MAX_PLAUSIBLE_SPEED) },
                // The guarded accumulated distance, so the speed profile shares the height
                // profile's X-axis exactly rather than re-deriving a noisier one from raw
                // coordinates.
                distance = accumulatedDistance,
            )
        )
        lastRouteSampleDistance = accumulatedDistance
    }

    /**
     * Reads the trip's altitude source — committed by [latchElevationSource] at the first
     * sample — into the ascent/descent totals and grade window. Only reached by a fix that
     * passed every guard above *and* advanced distance, the gating the elevation figures
     * rely on.
     */
    private fun sampleElevation(fix: LocationFix) {
        val altitude: Double? = when (elevationSource) {
            ElevationSource.BAROMETER -> altimeter.relativeAltitude
            ElevationSource.GPS -> fix.usableAltitude(ceiling = MAX_ALTITUDE_VERTICAL_ACCURACY)
            ElevationSource.UNDECIDED -> latchElevationSource(fix)
        }
        if (altitude == null) return

        // A break in sampling separates two readings that aren't comparable — see
        // ELEVATION_CONTINUITY_GAP_SECONDS. Anchor fresh and let the grade window refill.
        val lastSample = lastElevationSampleTimestamp
        if (lastSample != null &&
            fix.timestamp.secondsSince(lastSample) > ELEVATION_CONTINUITY_GAP_SECONDS
        ) {
            elevation.reanchor()
            grade.reset()
        }
        lastElevationSampleTimestamp = fix.timestamp

        elevation.add(altitude = altitude)
        _totalAscent.value = elevation.ascent
        _totalDescent.value = elevation.descent

        grade.add(distance = accumulatedDistance, altitude = altitude)
        _currentGrade.value = grade.grade
    }

    /**
     * Commits the trip to barometer or GPS altitude from data actually arriving, not hardware
     * presence: a barometer without its permission reports available yet never delivers. The
     * barometer gets a short grace for its first reading (it was only just started); GPS wins
     * when there is no barometer, when it has failed, or when the grace runs out.
     */
    private fun latchElevationSource(fix: LocationFix): Double? {
        if (altimeter.isAvailable) {
            val reading = altimeter.relativeAltitude
            if (reading != null) {
                elevationSource = ElevationSource.BAROMETER
                elevation = ElevationAccumulator(deadband = BAROMETER_DEADBAND)
                return reading
            }
            if (barometerGraceFixesRemaining > 0) {
                barometerGraceFixesRemaining -= 1
                return null
            }
        }
        elevationSource = ElevationSource.GPS
        elevation = ElevationAccumulator(deadband = GPS_ALTITUDE_DEADBAND)
        return fix.usableAltitude(ceiling = MAX_ALTITUDE_VERTICAL_ACCURACY)
    }

    private fun Instant.secondsSince(other: Instant): Double =
        (toEpochMilli() - other.toEpochMilli()) / 1000.0

    companion object {
        /** Guards against GPS teleport artifacts corrupting the trip total. ~120 km/h in m/s. */
        const val MAX_PLAUSIBLE_SPEED: Double = 120.0 / 3.6

        /**
         * A stationary phone's fix wanders by a metre or two; movement under
         * `max(this, 0.5 × horizontalAccuracy)` is treated as that wander.
         */
        const val JITTER_FLOOR_METERS: Double = 1.0

        const val MAX_TELEPORT_REJECTIONS: Int = 3

        const val SAVE_MIN_DURATION_SECONDS: Double = 5.0
        const val SAVE_MIN_DISTANCE_METERS: Double = 10.0

        /**
         * Auto-pause thresholds. Resume sits above pause (hysteresis): GPS speed drifts
         * around 0–1.5 km/h at a standstill, so a single cutoff would flap. Pausing waits out
         * the delay; resuming is immediate, so no moving time or distance is lost pulling
         * away from a light.
         */
        const val AUTO_PAUSE_SPEED: Double = 2.0 / 3.6 // 2 km/h in m/s
        const val AUTO_RESUME_SPEED: Double = 3.0 / 3.6 // 3 km/h in m/s
        const val AUTO_PAUSE_DELAY_SECONDS: Double = 3.0

        /**
         * How long an auto-pause may survive with no accepted fix to justify it. Fixes arrive
         * about once a second, so this gap means the signal went unusable, not that the rider
         * is holding still.
         */
        const val AUTO_PAUSE_FIX_TIMEOUT_SECONDS: Double = 5.0

        /**
         * Deadbands per altitude source: the barometer resolves to ~±1 m; the GPS fallback
         * wobbles by metres and needs the wider band.
         */
        const val BAROMETER_DEADBAND: Double = 1.0
        const val GPS_ALTITUDE_DEADBAND: Double = 3.0

        /**
         * Beyond this, GPS altitude's own error bar manufactures climb — a 30 m error against
         * a 3 m deadband — so such fixes contribute nothing.
         */
        const val MAX_ALTITUDE_VERTICAL_ACCURACY: Double = 15.0

        /**
         * How many fixes an undecided trip waits for the just-started barometer's first
         * reading before settling for GPS. Readings normally arrive within a second or two,
         * so this only decides the pathological silent case.
         */
        const val BAROMETER_LATCH_GRACE_FIXES: Int = 10

        /**
         * A sampling gap longer than this — a pause, standstill, or stretch of unusable fixes
         * — separates two readings that aren't comparable: whatever altitude did in between
         * wasn't ridden, so the next sample re-baselines instead of banking the gap.
         */
        const val ELEVATION_CONTINUITY_GAP_SECONDS: Double = 15.0

        const val GRADE_WINDOW_METERS: Double = 30.0
        const val ALTITUDE_SAMPLE_DISTANCE_INTERVAL: Double = 25.0
        const val ROUTE_SAMPLE_DISTANCE_INTERVAL: Double = 10.0
    }
}
