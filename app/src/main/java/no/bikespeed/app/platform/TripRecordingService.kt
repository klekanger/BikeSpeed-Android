package no.bikespeed.app.platform

import no.bikespeed.app.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * The Android answer to iOS's `UIBackgroundModes = location`: a foreground service with the
 * `location` type, held for exactly the span of a recording. While it runs, the fused
 * provider keeps delivering under a locked screen; the persistent notification is the
 * platform's equivalent of the iOS background-location pill.
 *
 * Driven entirely through `LocationSource.setBackgroundUpdates` — TripManager's already-
 * tested start/resume vs pause/reset calls get background recording for free, and an
 * auto-pause deliberately keeps the service alive: its only exit is a fix above the resume
 * threshold, which can never arrive under a locked screen once GPS is released.
 */
class TripRecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    // START_STICKY: if the OS reclaims the process mid-ride, restart the service — the
    // notification and location hold come back, and the rider's recording continues rather
    // than silently dying with the process.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "trip_recording"
        const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TripRecordingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripRecordingService::class.java))
        }

        fun createChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.recording_channel_name),
                    // Low importance: the notification is a status pill, not an alert — it
                    // must not buzz the handlebar every time a ride starts.
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }

        fun buildNotification(context: Context): Notification {
            val openApp = PendingIntent.getActivity(
                context,
                0,
                context.packageManager.getLaunchIntentForPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.recording_trip_title))
                .setContentText(context.getString(R.string.recording_trip_text))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setContentIntent(openApp)
                .build()
        }
    }
}

/**
 * The seam between `LocationManager.setBackgroundUpdates` and the service — injectable so the
 * source stays testable without a real service manager.
 */
fun interface RecordingServiceController {
    fun setRecording(enabled: Boolean)
}

/** The production controller: starts/stops [TripRecordingService]. */
class TripRecordingServiceController(private val context: Context) : RecordingServiceController {
    override fun setRecording(enabled: Boolean) {
        if (enabled) TripRecordingService.start(context) else TripRecordingService.stop(context)
    }
}
