package no.bikespeed.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import no.bikespeed.app.fixtures.TestClock
import no.bikespeed.app.fixtures.dateAtNoonUtc
import no.bikespeed.app.fixtures.makeEntry
import no.bikespeed.app.model.AltitudeSample
import no.bikespeed.app.model.RouteSample
import no.bikespeed.app.service.TripSyncEngine
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripDataStackTest {

    /**
     * Records every announcement — exists so that a mutation path added to TripDataStack a
     * year from now that forgets to notify the engine fails *here*, loudly, rather than
     * silently failing to sync somebody's rides.
     */
    private class SpyTripSyncEngine : TripSyncEngine {
        val changed = mutableListOf<UUID>()
        val deleted = mutableListOf<UUID>()
        override suspend fun tripDidChange(id: UUID) { changed.add(id) }
        override suspend fun tripWasDeleted(id: UUID) { deleted.add(id) }
        override suspend fun pullChanges() {}
    }

    private lateinit var database: BikeSpeedDatabase
    private lateinit var spy: SpyTripSyncEngine
    private lateinit var stack: TripDataStack
    private val clock = TestClock(start = Instant.ofEpochSecond(1_760_000_000))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = BikeSpeedDatabase.inMemory(context)
        spy = SpyTripSyncEngine()
        stack = TripDataStack(
            repository = TripRepository(database.tripDao()),
            sync = spy,
            now = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun makeRoute(count: Int = 3): List<RouteSample> = (0 until count).map { index ->
        RouteSample(
            latitude = 59.9139 + index * 0.001,
            longitude = 10.7522,
            altitude = 100.0 + index,
            timestamp = Instant.ofEpochSecond(1_760_000_000L + index),
            speed = 6.8,
            distance = index * 10.0,
        )
    }

    private fun makeProfile(count: Int = 3): List<AltitudeSample> = (0 until count).map { index ->
        AltitudeSample(distance = index * 25.0, altitude = 100.0 + index)
    }

    private suspend fun scalars(): List<TripScalars> {
        var result: List<TripScalars> = emptyList()
        stack.repository.observeScalars().test {
            result = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    // region Saving

    @Test
    fun `saving a trip makes it fetchable`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 14))

        stack.save(entry, altitudeProfile = makeProfile(), route = makeRoute())

        val saved = scalars()
        assertThat(saved).hasSize(1)
        assertThat(saved.first().id).isEqualTo(entry.id.toString())
    }

    @Test
    fun `a saved trip carries its track and its profile`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 14))
        val route = makeRoute(count = 5)
        val profile = makeProfile(count = 4)

        stack.save(entry, altitudeProfile = profile, route = route)

        val payloads = stack.repository.payloads(entry.id)
        val storedRoute = TripPayloadCoder.decodeRoute(payloads.routeData!!)
        val storedProfile = TripPayloadCoder.decodeAltitudeProfile(payloads.altitudeData!!)
        assertWithMessage("a track must round-trip sample for sample — it is the ride's map")
            .that(storedRoute).isEqualTo(route)
        assertThat(storedProfile).isEqualTo(profile)
    }

    /**
     * "An empty track is not a track." The blob stays null, so the detail view can keep
     * telling "no track recorded" apart from "a track of nothing".
     */
    @Test
    fun `saving a trip with no route stores no route blob`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 14))

        stack.save(entry, altitudeProfile = emptyList(), route = emptyList())

        val payloads = stack.repository.payloads(entry.id)
        assertThat(payloads.routeData).isNull()
        assertThat(payloads.altitudeData).isNull()
    }

    /**
     * The dirty clock a future sync reads. Nothing consumes it today, which is exactly why it
     * needs a test — a field nobody reads is a field that silently stops being written.
     */
    @Test
    fun `saving stamps updatedAt and leaves the sync fields clear`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 14))

        stack.save(entry, altitudeProfile = emptyList(), route = emptyList())

        val row = database.tripDao().let { dao ->
            // Read the raw row via a payload-free query plus the entity defaults we can
            // observe: updatedAt comes from the injected clock.
            dao.existingIds()
        }
        assertThat(row).containsExactly(entry.id.toString())
        // The entity carries the injected clock's time and clear sync fields — asserted
        // through a full-row read.
        val full = fullRow(entry.id)
        assertThat(full.updatedAt).isEqualTo(clock.now)
        assertWithMessage("a freshly saved trip owes the server everything")
            .that(full.syncedAt).isNull()
        assertThat(full.deletedAt).isNull()
        assertThat(full.remoteId).isNull()
    }

    // endregion

    // region Deleting

    /** The track is a column of the row, so it dies with it — no orphaned blob to reap. */
    @Test
    fun `deleting a trip takes its track with it`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 14))
        stack.save(entry, altitudeProfile = makeProfile(), route = makeRoute())

        stack.delete(listOf(entry.id))

        assertThat(scalars()).isEmpty()
        val payloads = stack.repository.payloads(entry.id)
        assertWithMessage("the track must not outlive the trip that owned it")
            .that(payloads.routeData).isNull()
        assertThat(payloads.altitudeData).isNull()
    }

    @Test
    fun `deleting a trip leaves the others alone`() = runTest {
        val keep = makeEntry(startDate = dateAtNoonUtc(2026, 7, 1))
        val drop = makeEntry(startDate = dateAtNoonUtc(2026, 7, 2))
        stack.save(keep, altitudeProfile = emptyList(), route = makeRoute())
        stack.save(drop, altitudeProfile = emptyList(), route = makeRoute())

        stack.delete(listOf(drop.id))

        assertThat(scalars().map { it.id }).containsExactly(keep.id.toString())
        assertWithMessage("the surviving trip must keep its own track")
            .that(stack.repository.payloads(keep.id).routeData).isNotNull()
    }

    // endregion

    // region The sync seam

    @Test
    fun `saving a trip notifies the sync engine`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 14))

        stack.save(entry, altitudeProfile = emptyList(), route = emptyList())

        assertThat(spy.changed).containsExactly(entry.id)
    }

    @Test
    fun `deleting a trip notifies the sync engine`() = runTest {
        val entry = makeEntry(startDate = dateAtNoonUtc(2026, 7, 14))
        stack.save(entry, altitudeProfile = emptyList(), route = emptyList())

        stack.delete(listOf(entry.id))

        assertThat(spy.deleted).containsExactly(entry.id)
    }

    // endregion

    private suspend fun fullRow(id: UUID): TripEntity {
        // A one-off raw read for assertions on the sync metadata; production code never
        // fetches full rows for a list.
        val payloadsAreIrrelevant = stack.repository.payloads(id)
        checkNotNull(payloadsAreIrrelevant)
        return database.query("SELECT * FROM trips WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            check(cursor.moveToFirst()) { "no row for $id" }
            fun col(name: String) = cursor.getColumnIndexOrThrow(name)
            TripEntity(
                id = cursor.getString(col("id")),
                startDate = Instant.ofEpochMilli(cursor.getLong(col("startDate"))),
                duration = cursor.getDouble(col("duration")),
                distance = cursor.getDouble(col("distance")),
                averageSpeed = cursor.getDouble(col("averageSpeed")),
                maxSpeed = cursor.getDouble(col("maxSpeed")),
                totalAscent = if (cursor.isNull(col("totalAscent"))) null else cursor.getDouble(col("totalAscent")),
                totalDescent = if (cursor.isNull(col("totalDescent"))) null else cursor.getDouble(col("totalDescent")),
                updatedAt = Instant.ofEpochMilli(cursor.getLong(col("updatedAt"))),
                deletedAt = if (cursor.isNull(col("deletedAt"))) null else Instant.ofEpochMilli(cursor.getLong(col("deletedAt"))),
                syncedAt = if (cursor.isNull(col("syncedAt"))) null else Instant.ofEpochMilli(cursor.getLong(col("syncedAt"))),
                remoteId = if (cursor.isNull(col("remoteId"))) null else cursor.getString(col("remoteId")),
            )
        }
    }
}
