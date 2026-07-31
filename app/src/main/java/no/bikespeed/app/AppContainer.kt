package no.bikespeed.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import no.bikespeed.app.data.BikeSpeedDatabase
import no.bikespeed.app.data.TripDataStack
import no.bikespeed.app.data.TripRepository
import no.bikespeed.app.service.AddressLookupManager
import no.bikespeed.app.service.AltitudeSource
import no.bikespeed.app.service.LocationManager
import no.bikespeed.app.service.ReverseGeocoder
import no.bikespeed.app.service.SettingsStore
import no.bikespeed.app.service.TripManager
import java.time.Instant
import no.bikespeed.app.service.GpxExporter
import no.bikespeed.app.service.NowProvider

/**
 * Manual DI — the app has one screen graph and a handful of singletons, which doesn't justify
 * a framework. Construction order matters and mirrors the iOS composition root: settings and
 * location first, then the trip manager (needs both), then address lookup (needs location),
 * then the data stack.
 */
class AppContainer(
    context: Context,
    /** The barometer; the platform implementation lands with the sensor glue. */
    altimeter: AltitudeSource,
    /** The street-name resolver; the platform Geocoder wrapper lands with the sensor glue. */
    geocoder: ReverseGeocoder,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsStore = SettingsStore(
        context.getSharedPreferences("bikespeed_settings", Context.MODE_PRIVATE)
    )

    val locationManager = LocationManager()

    val tripManager = TripManager(
        locationSource = locationManager,
        altimeter = altimeter,
        settings = settingsStore,
        now = NowProvider { Instant.now() },
    )

    val addressLookupManager = AddressLookupManager(
        locationSource = locationManager,
        geocoder = geocoder,
        scope = appScope,
    )

    val database = BikeSpeedDatabase.build(context)

    val tripDataStack = TripDataStack(repository = TripRepository(database.tripDao()))

    init {
        // Trip detail views leave .gpx files in the cache dir, which the OS only reaps under
        // storage pressure. Launch is the one moment no share sheet can still be reading one.
        GpxExporter.clearExports(context.cacheDir)
    }
}
