package no.bikespeed.app

import android.app.Application
import no.bikespeed.app.service.AltitudeSource
import no.bikespeed.app.service.ReverseGeocoder

/**
 * Constructs the [AppContainer]. The barometer and geocoder are inert stubs until the
 * platform sensor glue lands — the app runs, the gauge just sees no fixes yet.
 */
class BikeSpeedApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            context = this,
            altimeter = NoBarometer,
            geocoder = ReverseGeocoder { null },
        )
    }

    private object NoBarometer : AltitudeSource {
        override val isAvailable: Boolean = false
        override val relativeAltitude: Double? = null
        override fun startUpdates() {}
        override fun stopUpdates() {}
    }
}
