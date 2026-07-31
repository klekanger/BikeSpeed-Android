package no.bikespeed.app

import android.content.Context
import android.hardware.SensorManager
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import no.bikespeed.app.data.BikeSpeedDatabase
import no.bikespeed.app.data.TripDataStack
import no.bikespeed.app.data.TripRepository
import no.bikespeed.app.platform.BarometerAltitudeSource
import no.bikespeed.app.platform.FusedLocationProvider
import no.bikespeed.app.platform.GeocoderReverseGeocoder
import no.bikespeed.app.platform.GravityTiltProvider
import no.bikespeed.app.platform.HeadingProvider
import no.bikespeed.app.platform.TripRecordingServiceController
import no.bikespeed.app.service.AddressLookupManager
import no.bikespeed.app.service.GpxExporter
import no.bikespeed.app.service.LocationManager
import no.bikespeed.app.service.NowProvider
import no.bikespeed.app.service.SettingsStore
import no.bikespeed.app.service.TripManager

/**
 * Manual DI — the app has one screen graph and a handful of singletons, which doesn't justify
 * a framework. Construction order matters and mirrors the iOS composition root: settings and
 * location first, then the trip manager (needs both), then address lookup (needs location),
 * then the data stack.
 */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val settingsStore = SettingsStore(
        context.getSharedPreferences("bikespeed_settings", Context.MODE_PRIVATE)
    )

    val locationManager = LocationManager()

    /** The barometer. Not exposed to any UI — TripManager is its only consumer. */
    private val altimeter = BarometerAltitudeSource(sensorManager)

    val tripManager = TripManager(
        locationSource = locationManager,
        altimeter = altimeter,
        settings = settingsStore,
        now = NowProvider { Instant.now() },
    )

    val addressLookupManager = AddressLookupManager(
        locationSource = locationManager,
        geocoder = GeocoderReverseGeocoder(context),
        scope = appScope,
    )

    val database = BikeSpeedDatabase.build(context)

    val tripDataStack = TripDataStack(repository = TripRepository(database.tripDao()))

    // Platform sensor glue: started once the location permission is granted.
    private val fusedLocation = FusedLocationProvider(context, locationManager)
    private val headingProvider = HeadingProvider(sensorManager, locationManager)
    val tiltProvider = GravityTiltProvider(sensorManager)

    init {
        // Background recording: TripManager's setBackgroundUpdates calls (on at start/resume,
        // off at pause/reset, held through an auto-pause) drive the foreground service.
        val serviceController = TripRecordingServiceController(context)
        locationManager.backgroundUpdatesHandler = { enabled ->
            serviceController.setRecording(enabled)
        }
    }

    private var sensorsStarted = false

    /**
     * Starts the live sensors. Requires the location permission; idempotent, so the
     * permission callback and a fresh launch with the grant already in place can both call it.
     */
    fun startSensors() {
        if (sensorsStarted) return
        sensorsStarted = true
        fusedLocation.start()
        headingProvider.start()
        tiltProvider.start()
    }

    init {
        // Trip detail views leave .gpx files in the cache dir, which the OS only reaps under
        // storage pressure. Launch is the one moment no share sheet can still be reading one.
        GpxExporter.clearExports(context.cacheDir)
    }
}
