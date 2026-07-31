package no.bikespeed.app.ui.detail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import no.bikespeed.app.data.BikeSpeedDatabase
import no.bikespeed.app.data.TripDataStack
import no.bikespeed.app.data.TripRepository
import no.bikespeed.app.fixtures.dateAtNoonUtc
import no.bikespeed.app.fixtures.makeEntry
import no.bikespeed.app.model.AltitudeSample
import no.bikespeed.app.model.RouteSample
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripDetailLoaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: BikeSpeedDatabase
    private lateinit var stack: TripDataStack
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = BikeSpeedDatabase.inMemory(context)
        stack = TripDataStack(repository = TripRepository(database.tripDao()))
        cacheDir = folder.newFolder()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun loader() = TripDetailLoader(repository = stack.repository, cacheDir = cacheDir)

    private fun route(count: Int): List<RouteSample> = (0 until count).map { index ->
        RouteSample(
            latitude = 59.9139 + index * 10.0 / 111_320,
            longitude = 10.7522,
            altitude = 100.0 + index,
            timestamp = Instant.ofEpochSecond(1_760_000_000L + index),
            speed = 6.8,
            distance = index * 10.0,
        )
    }

    @Test
    fun `a full trip loads its profile, speed profile, coordinates, and gpx`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 4))
        stack.save(
            entry,
            altitudeProfile = listOf(AltitudeSample(0.0, 100.0), AltitudeSample(25.0, 110.0)),
            route = route(count = 10),
        )

        val loaded = loader().load(entry, trackName = "4 Jul 2026, 09:15", fileName = "BikeSpeed-2026-07-04-0915")

        assertThat(loaded.profile).hasSize(2)
        assertThat(loaded.speedProfile).isNotEmpty()
        assertThat(loaded.coordinates).hasSize(10)
        val gpx = loaded.gpxFile!!
        assertThat(gpx.name).isEqualTo("BikeSpeed-2026-07-04-0915.gpx")
        assertWithMessage("one export directory per trip id, under GPXExports")
            .that(gpx.parentFile!!.name).isEqualTo(entry.id.toString())
        assertThat(gpx.parentFile!!.parentFile!!.name).isEqualTo("GPXExports")
    }

    /**
     * A trip recorded without altitude has an empty profile — the chart then shows its
     * "no data" state rather than a flat lie at zero.
     */
    @Test
    fun `a trip without altitude data loads an empty profile but keeps its track`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 4))
        stack.save(entry, altitudeProfile = emptyList(), route = route(count = 5))

        val loaded = loader().load(entry, trackName = "Ride", fileName = "ride")

        assertThat(loaded.profile).isEmpty()
        assertThat(loaded.coordinates).hasSize(5)
        assertThat(loaded.speedProfile).isNotEmpty()
    }

    /** A trip with no track at all: nothing to map, nothing to share. */
    @Test
    fun `a trip without a route loads no coordinates and writes no gpx`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 4))
        stack.save(entry, altitudeProfile = listOf(AltitudeSample(0.0, 100.0)), route = emptyList())

        val loaded = loader().load(entry, trackName = "Ride", fileName = "ride")

        assertThat(loaded.coordinates).isEmpty()
        assertThat(loaded.speedProfile).isEmpty()
        assertThat(loaded.gpxFile).isNull()
        assertWithMessage("the height profile is independent of the track")
            .that(loaded.profile).hasSize(1)
    }
}
