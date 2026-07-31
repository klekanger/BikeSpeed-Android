package no.bikespeed.app.service

import com.google.common.truth.Truth.assertThat
import no.bikespeed.app.fixtures.InMemorySharedPreferences
import no.bikespeed.app.model.AppLanguage
import no.bikespeed.app.model.MeasurementSystem
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun `settings survive a relaunch`() {
        val prefs = InMemorySharedPreferences()
        val store = SettingsStore(prefs)
        store.maxGaugeSpeedKmh = 80.0
        store.measurementSystem = MeasurementSystem.IMPERIAL
        store.appLanguage = AppLanguage.NORWEGIAN
        store.autoPauseEnabled = false

        val relaunched = SettingsStore(prefs)

        assertThat(relaunched.maxGaugeSpeedKmh).isEqualTo(80.0)
        assertThat(relaunched.measurementSystem).isEqualTo(MeasurementSystem.IMPERIAL)
        assertThat(relaunched.appLanguage).isEqualTo(AppLanguage.NORWEGIAN)
        assertThat(relaunched.autoPauseEnabled).isFalse()
    }

    @Test
    fun `a fresh install gets the documented defaults`() {
        val store = SettingsStore(InMemorySharedPreferences())

        assertThat(store.maxGaugeSpeedKmh).isEqualTo(60.0)
        assertThat(store.measurementSystem).isEqualTo(MeasurementSystem.METRIC)
        assertThat(store.appLanguage).isEqualTo(AppLanguage.SYSTEM)
    }

    /**
     * `getBoolean(key, false)` reports an unset key as `false`, which is indistinguishable
     * from the rider having deliberately switched auto-pause off — so the store reads it
     * through `contains()` instead. Get this wrong and auto-pause silently ships disabled
     * for every new install.
     */
    @Test
    fun `auto-pause defaults to on when nothing has been saved`() {
        val store = SettingsStore(InMemorySharedPreferences())
        assertThat(store.autoPauseEnabled).isTrue()
    }

    /** And an explicit "off" must still survive, rather than the default-on stamping over it. */
    @Test
    fun `an explicitly disabled auto-pause is not overridden by the default`() {
        val prefs = InMemorySharedPreferences()
        SettingsStore(prefs).autoPauseEnabled = false

        assertThat(SettingsStore(prefs).autoPauseEnabled).isFalse()
    }

    /** A non-round gauge max (an mph conversion) must round-trip without float drift. */
    @Test
    fun `a converted gauge maximum survives storage exactly`() {
        val prefs = InMemorySharedPreferences()
        val canonical = MeasurementSystem.IMPERIAL.canonicalKmh(fromMaxGaugeSpeedValue = 60.0)
        SettingsStore(prefs).maxGaugeSpeedKmh = canonical

        assertThat(SettingsStore(prefs).maxGaugeSpeedKmh).isEqualTo(canonical)
    }
}
