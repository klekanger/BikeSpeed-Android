package no.bikespeed.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the Norwegian resources — above all the compass east/west axis swap (E → Ø, W → V),
 * which is the one localization the iOS catalog calls out as load-bearing: a bare "E" on a
 * Norwegian screen reads as the wrong letter, not as untranslated English.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "nb")
class NorwegianLocalizationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the compass east-west axis swaps to Ø and V`() {
        assertThat(context.getString(R.string.compass_e)).isEqualTo("Ø")
        assertThat(context.getString(R.string.compass_w)).isEqualTo("V")
        assertThat(context.getString(R.string.compass_ne)).isEqualTo("NØ")
        assertThat(context.getString(R.string.compass_sw)).isEqualTo("SV")
        // North and south stay as they are.
        assertThat(context.getString(R.string.compass_n)).isEqualTo("N")
        assertThat(context.getString(R.string.compass_s)).isEqualTo("S")
    }

    @Test
    fun `screen strings resolve in Norwegian`() {
        assertThat(context.getString(R.string.trip_log)).isEqualTo("Turlogg")
        assertThat(context.getString(R.string.settings)).isEqualTo("Innstillinger")
        assertThat(context.getString(R.string.auto_paused)).isEqualTo("Autopauset")
        assertThat(context.getString(R.string.resume)).isEqualTo("Fortsett")
        assertThat(context.getString(R.string.no_trips_logged)).isEqualTo("Ingen turer registrert ennå")
    }
}

@RunWith(RobolectricTestRunner::class)
class EnglishLocalizationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The "Gauge" section deliberately reads "Speedometer" — key and value differ on iOS too. */
    @Test
    fun `the gauge section is titled Speedometer`() {
        assertThat(context.getString(R.string.section_gauge)).isEqualTo("Speedometer")
    }

    @Test
    fun `default resources are English`() {
        assertThat(context.getString(R.string.compass_e)).isEqualTo("E")
        assertThat(context.getString(R.string.trip_log)).isEqualTo("Trip Log")
    }
}
