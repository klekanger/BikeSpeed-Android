package no.bikespeed.app.ui.log

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import no.bikespeed.app.data.BikeSpeedDatabase
import no.bikespeed.app.data.TripDataStack
import no.bikespeed.app.data.TripRepository
import no.bikespeed.app.fixtures.InMemorySharedPreferences
import no.bikespeed.app.fixtures.dateAtNoonUtc
import no.bikespeed.app.fixtures.makeEntry
import no.bikespeed.app.service.SettingsStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripLogScreenTest {

    @get:org.junit.Rule
    val compose = createComposeRule()

    private lateinit var database: BikeSpeedDatabase
    private lateinit var stack: TripDataStack
    private val settings = SettingsStore(InMemorySharedPreferences())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = BikeSpeedDatabase.inMemory(context)
        stack = TripDataStack(repository = TripRepository(database.tripDao()))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an empty log shows its empty state`() {
        compose.setContent {
            TripLogScreen(stack = stack, settings = settings, onTrip = {}, onBack = {})
        }
        compose.waitForIdle()

        compose.onNodeWithTag("emptyLog").assertIsDisplayed()
    }

    @Test
    fun `a saved trip appears in the list with the summary above it`() {
        runBlocking {
            stack.save(
                makeEntry(startDate = dateAtNoonUtc(2026, 7, 10), distance = 12_400.0),
                altitudeProfile = emptyList(),
                route = emptyList(),
            )
        }

        compose.setContent {
            TripLogScreen(stack = stack, settings = settings, onTrip = {}, onBack = {})
        }
        compose.waitForIdle()

        compose.onNodeWithText("Rides").assertIsDisplayed()
        // The distance legitimately appears several times (the row, the total, this month) —
        // assert at least one is on screen.
        compose.onAllNodesWithText("12.40 km").onFirst().assertIsDisplayed()
    }
}
