package no.bikespeed.app

import android.Manifest
import android.os.Build

/**
 * The pure decisions behind MainActivity's permission flow, extracted so they're testable:
 * whether a grant result actually unlocks the sensors, and which permissions a launch still
 * needs to ask for.
 */
object PermissionGating {

    /**
     * The sensors need *location*. The grant result also carries POST_NOTIFICATIONS, so an
     * any-of check would start a location request on a notifications-only grant — a
     * SecurityException in the fused provider, repeated on every launch because the grant
     * state persists.
     */
    fun grantsLocation(grants: Map<String, Boolean>): Boolean =
        grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

    /**
     * What this launch still needs to ask for. The notification permission (API 33+, for the
     * recording service's persistent pill; a denial doesn't block recording) is requested
     * whenever it's missing — *including* launches where location is already held, so a user
     * who granted location but dismissed the notification prompt isn't left unasked forever.
     */
    fun permissionsToRequest(
        hasLocation: Boolean,
        hasNotifications: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): List<String> = buildList {
        if (!hasLocation) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (sdkInt >= 33 && !hasNotifications) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
