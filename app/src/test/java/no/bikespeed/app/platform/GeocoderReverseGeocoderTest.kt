package no.bikespeed.app.platform

import android.location.Address
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.Locale
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pre-33 result contract. `Geocoder.getFromLocation` documents a null return as possibly
 * meaning "no backend service available" — a *failure*. Coercing it to an empty list would
 * report a resolved "no street here": the held street would blank, the failure backoff would
 * never arm, and the manager would keep polling a dead service at full cadence. iOS draws
 * the same line: only geocodeFoundNoResult counts as resolved.
 */
@RunWith(RobolectricTestRunner::class)
class GeocoderReverseGeocoderTest {

    @Test
    fun `a null result is a backend failure, not an empty answer`() {
        assertThrows(IOException::class.java) {
            GeocoderReverseGeocoder.requireBackend(null)
        }
    }

    @Test
    fun `an actual result passes through as resolved`() {
        assertThat(GeocoderReverseGeocoder.requireBackend(emptyList())).isEmpty()

        val address = Address(Locale.getDefault()).apply { thoroughfare = "Storgata" }
        assertThat(GeocoderReverseGeocoder.requireBackend(listOf(address)))
            .containsExactly(address)
    }
}
