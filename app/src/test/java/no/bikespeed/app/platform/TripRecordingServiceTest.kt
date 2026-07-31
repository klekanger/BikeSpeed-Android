package no.bikespeed.app.platform

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import no.bikespeed.app.service.LocationManager
import org.junit.Test
import org.junit.runner.RunWith
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
