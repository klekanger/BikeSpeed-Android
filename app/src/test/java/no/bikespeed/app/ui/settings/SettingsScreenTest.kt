package no.bikespeed.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import no.bikespeed.app.fixtures.InMemorySharedPreferences
import no.bikespeed.app.model.MeasurementSystem
import no.bikespeed.app.service.SettingsStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the auto-pause toggle writes the store`() {
        val store = SettingsStore(InMemorySharedPreferences())
        compose.setContent { SettingsScreen(settings = store, onBack = {}) }

        compose.onNodeWithTag("autoPauseToggle").performClick()
        compose.waitForIdle()

        assertThat(store.autoPauseEnabled).isFalse()
    }

    @Test
    fun `the stepper round-trips through canonical kilometers per hour`() {
        val store = SettingsStore(InMemorySharedPreferences()) // 60 km/h default
        store.measurementSystem = MeasurementSystem.IMPERIAL
        compose.setContent { SettingsScreen(settings = store, onBack = {}) }

        // 60 km/h shows as 37.28 mph; a press up snaps to 40 mph, stored back as km/h.
        compose.onNodeWithTag("maxSpeedUp").performClick()
        compose.waitForIdle()

        assertThat(MeasurementSystem.IMPERIAL.maxGaugeSpeedValue(store.maxGaugeSpeedKmh))
            .isWithin(0.001).of(40.0)
        compose.onNodeWithText("40 mph").assertIsDisplayed()
    }

    @Test
    fun `the unit picker switches the system`() {
        val store = SettingsStore(InMemorySharedPreferences())
        compose.setContent { SettingsScreen(settings = store, onBack = {}) }

        compose.onNodeWithTag("unit_imperial").performClick()
        compose.waitForIdle()

        assertThat(store.measurementSystem).isEqualTo(MeasurementSystem.IMPERIAL)
    }
}
