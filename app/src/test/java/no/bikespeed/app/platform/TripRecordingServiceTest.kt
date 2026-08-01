package no.bikespeed.app.platform

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import no.bikespeed.app.service.LocationManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The seam between the trip lifecycle and the foreground service. TripManager's own suite
 * already pins *when* background updates are requested (on at start/resume, off at
 * pause/reset, held through an auto-pause); these pin that the request actually reaches a
 * service controller, and the notification plumbing.
 */
@RunWith(RobolectricTestRunner::class)
class TripRecordingServiceTest {

    @Test
    fun `setBackgroundUpdates drives the injected service controller`() {
        val manager = LocationManager()
        val calls = mutableListOf<Boolean>()
        manager.backgroundUpdatesHandler = { enabled -> calls.add(enabled) }

        manager.setBackgroundUpdates(true)
        manager.setBackgroundUpdates(false)

        assertThat(calls).containsExactly(true, false).inOrder()
    }

    @Test
    fun `the notification channel is low importance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        TripRecordingService.createChannel(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(TripRecordingService.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertWithMessage("a status pill must not buzz the handlebar every ride")
            .that(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    /**
     * START_NOT_STICKY, deliberately: the trip lives in memory, so after a process death
     * there is nothing left to record. A sticky restart would come back into a fresh process
     * whose TripManager is idle and whose sensors are stopped — an ongoing "Recording trip"
     * notification and a location foreground service that no code path can ever stop,
     * lying about a recording that no longer exists. iOS behaves the same way: background
     * location dies with the app.
     */
    @Test
    fun `a restart after process death does not resurrect a dead recording`() {
        val service = Robolectric.buildService(TripRecordingService::class.java).create().get()

        val result = service.onStartCommand(null, 0, 1)

        assertWithMessage("a sticky restart would be a permanent zombie foreground service")
            .that(result).isEqualTo(Service.START_NOT_STICKY)
    }

    @Test
    fun `the recording notification is ongoing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TripRecordingService.createChannel(context)

        val notification = TripRecordingService.buildNotification(context)

        assertWithMessage("the rider must not be able to swipe the recording pill away")
            .that(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.channelId).isEqualTo(TripRecordingService.CHANNEL_ID)
    }
}
