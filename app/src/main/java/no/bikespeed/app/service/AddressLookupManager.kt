package no.bikespeed.app.service

import java.time.Instant
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.bikespeed.app.model.LocationFix

/**
 * Resolves a fix to the name of the street the rider is on, or null when there is genuinely
 * no street here (trail, out at sea). Throws on *failure* (network down, throttled) — the
 * two outcomes are handled differently by [AddressLookupManager].
 *
 * The platform implementation wraps `android.location.Geocoder` and returns
 * `Address.thoroughfare` directly — unlike iOS's MapKit path, no formatted-address parsing
 * is needed.
 */
fun interface ReverseGeocoder {
    suspend fun streetName(fix: LocationFix): String?
}

/**
 * Reverse-geocodes accepted GPS fixes into the name of the street the rider is currently on.
 *
 * Subscribes to `LocationManager`'s accepted fixes (like `TripManager`), so it only sees
 * positions past the accuracy filter and stays live regardless of trip state.
 *
 * Reverse geocoding is a network call and the platform throttles callers that ask too often,
 * so a lookup fires only once the rider has moved [MINIMUM_DISTANCE_METERS] *and*
 * [MINIMUM_LOOKUP_INTERVAL_SECONDS] has passed since the last attempt. At 20 km/h, 75 m is
 * ~13 s, so the time gate is what keeps a real ride from being throttled.
 *
 * Consecutive failures back the interval off exponentially up to
 * [MAXIMUM_LOOKUP_INTERVAL_SECONDS]: if we *are* throttled, retrying at a fixed 10 s only
 * keeps us throttled.
 */
class AddressLookupManager(
    locationSource: LocationSource,
    private val geocoder: ReverseGeocoder,
    private val scope: CoroutineScope,
    private val now: NowProvider = NowProvider { Instant.now() },
) {

    private val _streetName = MutableStateFlow<String?>(null)

    val streetNameFlow: StateFlow<String?> = _streetName.asStateFlow()

    /**
     * The street the rider is on, or null when unknown (nothing resolved yet, or genuinely
     * off-road). Held at its last value across geocoding *failures* — a network blip is no
     * reason to drop a good answer.
     */
    val streetName: String? get() = _streetName.value

    /** Position of the last *resolved* lookup — anchor for the distance gate. */
    private var lastLookupFix: LocationFix? = null

    /** Time of the last *attempt*, resolved or not — anchor for the interval gate. */
    private var lastLookupAttempt: Instant? = null

    /** Internal so tests can pin that a launch into a dead scope can't latch it true. */
    internal var isLookupInProgress = false
        private set

    private var consecutiveFailures = 0

    init {
        locationSource.addFixListener { consume(it) }
    }

    /**
     * Wait before the next attempt: base interval while healthy, doubling per consecutive
     * failure (10 s → 20 s → 40 s …) up to the maximum.
     */
    private val currentLookupIntervalSeconds: Double
        get() = min(
            MINIMUM_LOOKUP_INTERVAL_SECONDS * 2.0.pow(consecutiveFailures),
            MAXIMUM_LOOKUP_INTERVAL_SECONDS,
        )

    private fun consume(fix: LocationFix) {
        if (isLookupInProgress) return

        val lastLookupFix = this.lastLookupFix
        if (lastLookupFix != null && fix.distanceTo(lastLookupFix) < MINIMUM_DISTANCE_METERS) {
            return
        }
        val lastLookupAttempt = this.lastLookupAttempt
        if (lastLookupAttempt != null &&
            (now.now().toEpochMilli() - lastLookupAttempt.toEpochMilli()) / 1000.0 < currentLookupIntervalSeconds
        ) {
            return
        }

        isLookupInProgress = true
        this.lastLookupAttempt = now.now()

        // The guard reset rides the job's *completion*, not a `finally`: a launch into a
        // scope that is already cancelled never runs its body or its finally, which would
        // latch the guard true and silently drop every future lookup.
        scope.launch {
            try {
                // A resolved null is not a failure — genuinely no street here. Blank the
                // caption and let the gates advance.
                resolve(geocoder.streetName(fix), fix)
            } catch (e: CancellationException) {
                // A teardown, not a geocoder failure — arming the backoff here would delay
                // the next genuine attempt for no reason.
                throw e
            } catch (_: Exception) {
                // Network down or throttled. Keep the last resolved street and leave
                // lastLookupFix put so the next fix retries — but back off first, so a
                // throttle doesn't become hammering.
                consecutiveFailures += 1
            }
        }.invokeOnCompletion { isLookupInProgress = false }
    }

    private fun resolve(street: String?, fix: LocationFix) {
        lastLookupFix = fix
        consecutiveFailures = 0
        // One street spans many lookup windows, so most resolutions repeat the last answer;
        // StateFlow already dedups equal values.
        _streetName.value = street
    }

    companion object {
        const val MINIMUM_DISTANCE_METERS: Double = 75.0
        const val MINIMUM_LOOKUP_INTERVAL_SECONDS: Double = 10.0
        const val MAXIMUM_LOOKUP_INTERVAL_SECONDS: Double = 120.0
    }
}
