package no.bikespeed.app

import android.Manifest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The decisions behind MainActivity's permission flow. The load-bearing one: only a
 * *location* grant may start the sensors — the multi-permission request also carries
 * POST_NOTIFICATIONS, and a user who allows notifications while denying location must not
 * trigger a location request the app has no right to make (SecurityException, and because
 * the grant state persists, a crash loop on every launch).
 */
class PermissionGatingTest {

    @Test
    fun `a notifications-only grant does not unlock the sensors`() {
        val grants = mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to false,
            Manifest.permission.ACCESS_COARSE_LOCATION to false,
            Manifest.permission.POST_NOTIFICATIONS to true,
        )

        assertWithMessage("starting sensors without a location grant crashes in the fused provider")
            .that(PermissionGating.grantsLocation(grants)).isFalse()
    }

    @Test
    fun `either location grant unlocks the sensors`() {
        assertThat(
            PermissionGating.grantsLocation(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))
        ).isTrue()
        // "Approximate location only" is a choice the system dialog offers.
        assertThat(
            PermissionGating.grantsLocation(mapOf(Manifest.permission.ACCESS_COARSE_LOCATION to true))
        ).isTrue()
        assertThat(PermissionGating.grantsLocation(emptyMap())).isFalse()
    }

    @Test
    fun `a launch without location asks for location`() {
        val requests = PermissionGating.permissionsToRequest(
            hasLocation = false,
            hasNotifications = false,
            sdkInt = 33,
        )

        assertThat(requests).containsAtLeast(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    /**
     * The regression this pins: location already held must not skip the notification ask —
     * otherwise a user who granted location but dismissed the notification prompt is never
     * asked again, and the API 33+ recording pill can never show.
     */
    @Test
    fun `a launch with location but without notifications still asks for notifications`() {
        val requests = PermissionGating.permissionsToRequest(
            hasLocation = true,
            hasNotifications = false,
            sdkInt = 33,
        )

        assertThat(requests).containsExactly(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Below API 33 the notification permission does not exist and must not be requested. */
    @Test
    fun `pre-33 devices are never asked for the notification permission`() {
        val requests = PermissionGating.permissionsToRequest(
            hasLocation = true,
            hasNotifications = false,
            sdkInt = 30,
        )

        assertThat(requests).isEmpty()
    }

    @Test
    fun `nothing is requested when everything is held`() {
        val requests = PermissionGating.permissionsToRequest(
            hasLocation = true,
            hasNotifications = true,
            sdkInt = 34,
        )

        assertThat(requests).isEmpty()
    }
}
