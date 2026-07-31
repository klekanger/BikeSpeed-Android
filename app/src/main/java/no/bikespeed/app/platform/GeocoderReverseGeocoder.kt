package no.bikespeed.app.platform

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
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
                geocoder.getFromLocation(fix.latitude, fix.longitude, 1) ?: emptyList()
            }
        }
        return addresses.firstOrNull()?.thoroughfare
    }
}
