package no.bikespeed.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Picking a language in Settings must retranslate the screen the rider is looking at, not
 * only the next one. On API 29–32 AppCompat's per-app-locale backport applies the change by
 * calling Activity.onConfigurationChanged itself and never dispatches it to the views, so
 * Compose kept its old strings until a navigation recomposed them (seen on an Android 10 Sony).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LanguageSwitchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    // The backport keeps the app locales in a static, which outlives the test's activity.
    @After
    fun resetLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun `switching the language retranslates the open settings screen`() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()

        compose.onNodeWithTag("language_norwegian").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Innstillinger").assertIsDisplayed()
    }

    // Unit labels are formatted with a Locale, not looked up as resources — and on API 29–32
    // Locale.getDefault() doesn't follow the backport, so the label must come from the
    // configuration the strings follow.
    @Test
    fun `switching the language reformats the open screen's units`() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("maxSpeedValue").assertTextEquals("60 km/h")

        compose.onNodeWithTag("language_norwegian").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("maxSpeedValue").assertTextEquals("60 km/t")
    }
}
