package no.bikespeed.app.service

import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.bikespeed.app.model.LocationFix
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
    }

    fun resume() {
        if (state != TripState.PAUSED) return
        startClock()
        _state.value = TripState.RUNNING
        locationSource.setBackgroundUpdates(true)
        altimeter.startUpdates()
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
        _state.value = TripState.IDLE
        locationSource.setBackgroundUpdates(false)
        altimeter.stopUpdates()
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
    }
}
