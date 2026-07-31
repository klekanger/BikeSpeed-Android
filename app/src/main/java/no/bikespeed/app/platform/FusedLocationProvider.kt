package no.bikespeed.app.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import no.bikespeed.app.service.LocationManager

/**
 * Feeds fused-location fixes into [LocationManager.process] — the Android analogue of the
 * CLLocationManager delegate. High accuracy, ~1 fix/second, no distance filter, callbacks on
 * the main looper so delivery into the synchronous filter pipeline stays single-threaded.
 *
 * No automatic pausing: stopping is `TripManager`'s call (auto-pause), never the OS's.
 */
class FusedLocationProvider(
    context: Context,
    private val locationManager: LocationManager,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var isStarted = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            locationManager.process(location.toLocationFix())
        }
    }

    /** Caller is responsible for holding the location permission before starting. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isStarted) return
        isStarted = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        client.removeLocationUpdates(callback)
    }
}
