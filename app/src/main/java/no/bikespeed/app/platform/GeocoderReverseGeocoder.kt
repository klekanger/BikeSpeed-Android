package no.bikespeed.app.platform

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import no.bikespeed.app.model.LocationFix
import no.bikespeed.app.service.ReverseGeocoder

/**
 * The platform geocoder behind the [ReverseGeocoder] seam. `Address.thoroughfare` is the bare
 * street name directly — no formatted-address parsing needed (the iOS legacy-path
 * equivalent). Contract: null = genuinely no street here (empty result), thrown exception =
 * failure (network down) — [no.bikespeed.app.service.AddressLookupManager] treats the two
 * differently.
 */
class GeocoderReverseGeocoder(private val context: Context) : ReverseGeocoder {

    override suspend fun streetName(fix: LocationFix): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context)
        val addresses: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    fix.latitude,
                    fix.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resumeWithException(
                                RuntimeException(errorMessage ?: "reverse geocoding failed")
                            )
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                requireBackend(geocoder.getFromLocation(fix.latitude, fix.longitude, 1))
            }
        }
        return addresses.firstOrNull()?.thoroughfare
    }

    companion object {
        /**
         * Pre-33, `getFromLocation` documents a null return as possibly meaning "no backend
         * service available" — a *failure*. Coercing it to an empty list would report a
         * resolved "no street here": the held street would blank, the failure backoff would
         * never arm, and the manager would keep polling a dead service at full cadence.
         * Only an actual (possibly empty) list is a resolved answer — iOS draws the same
         * line, treating only geocodeFoundNoResult as resolved.
         */
        internal fun requireBackend(addresses: List<Address>?): List<Address> =
            addresses ?: throw IOException("no geocoder backend available")
    }
}
