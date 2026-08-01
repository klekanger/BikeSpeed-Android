package no.bikespeed.app

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sensor-release policy for background transitions: a RUNNING trip (auto-paused
 * included — the resume fix must be able to arrive) keeps the sensors alive under a locked
 * screen, because the foreground service is holding the process for exactly that; anything
 * else releases GPS and the 16–20 Hz sensor listeners, which would otherwise run for the
 * life of the process as pure battery drain.
 */
@RunWith(RobolectricTestRunner::class)
class AppContainerTest {

    private fun container(): AppContainer =
        AppContainer(ApplicationProvider.getApplicationContext())

    @Test
    fun `a running trip keeps the sensors alive through a background transition`() {
        val container = container()
        container.startSensors()
        container.tripManager.start()

        container.releaseSensorsUnlessRecording()

        assertWithMessage("a recording needs its fixes even under a locked screen")
            .that(container.sensorsStarted).isTrue()
    }

    @Test
    fun `backgrounding with no recording releases the sensors and foregrounding restarts them`() {
        val container = container()
        container.startSensors()

        container.releaseSensorsUnlessRecording()
        assertThat(container.sensorsStarted).isFalse()

        container.startSensors() // onStart on the way back in
        assertThat(container.sensorsStarted).isTrue()
    }

    @Test
    fun `a paused trip does not hold the sensors`() {
        val container = container()
        container.startSensors()
        container.tripManager.start()
        container.tripManager.pause()

        container.releaseSensorsUnlessRecording()

        assertWithMessage("a paused trip accrues nothing; resume requires the app in front anyway")
            .that(container.sensorsStarted).isFalse()
    }
}
