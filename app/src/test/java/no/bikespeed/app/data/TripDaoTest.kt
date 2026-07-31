package no.bikespeed.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import kotlinx.coroutines.test.runTest
import no.bikespeed.app.fixtures.dateAtNoonUtc
import no.bikespeed.app.fixtures.makeEntry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The DAO against a real in-memory SQLite database — the analogue of the iOS in-memory
 * ModelContainer tests: parallel-safe, nothing on disk.
 */
@RunWith(RobolectricTestRunner::class)
class TripDaoTest {

    private lateinit var database: BikeSpeedDatabase
    private lateinit var dao: TripDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = BikeSpeedDatabase.inMemory(context)
        dao = database.tripDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        startDate: Instant = dateAtNoonUtc(2026, 7, 14),
        deletedAt: Instant? = null,
        routeData: ByteArray? = null,
        altitudeData: ByteArray? = null,
    ): TripEntity = TripEntity.from(
        entry = makeEntry(startDate = startDate),
        altitudeData = altitudeData,
        routeData = routeData,
        updatedAt = startDate,
    ).copy(deletedAt = deletedAt)

    @Test
    fun `a saved trip reads back through the scalar list, newest first`() = runTest {
        val older = entity(startDate = dateAtNoonUtc(2026, 6, 1))
        val newer = entity(startDate = dateAtNoonUtc(2026, 7, 1))
        dao.insert(listOf(older, newer))

        dao.observeScalars().test {
            val trips = awaitItem()
            assertThat(trips.map { it.id }).containsExactly(newer.id, older.id).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The soft-delete seam is pre-wired: a tombstoned row never reaches the list. */
    @Test
    fun `a tombstoned trip is filtered from the list`() = runTest {
        val visible = entity(startDate = dateAtNoonUtc(2026, 7, 1))
        val deleted = entity(
            startDate = dateAtNoonUtc(2026, 7, 2),
            deletedAt = dateAtNoonUtc(2026, 7, 3),
        )
        dao.insert(listOf(visible, deleted))

        dao.observeScalars().test {
            assertThat(awaitItem().map { it.id }).containsExactly(visible.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The list projection carries the scalars a row needs and nothing else — the entity's
     * blobs stay unread however large the track is, which is the whole reason TripScalars
     * exists.
     */
    @Test
    fun `the list works without touching the payload blobs`() = runTest {
        val heavy = entity(
            routeData = ByteArray(1_000_000), // a monster track the list must never load
            altitudeData = ByteArray(100_000),
        )
        dao.insert(listOf(heavy))

        dao.observeScalars().test {
            val trip = awaitItem().single()
            assertThat(trip.id).isEqualTo(heavy.id)
            assertThat(trip.toEntry().distance).isWithin(0.5).of(10_000.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `payloads come back byte-for-byte`() = runTest {
        val route = "route-bytes".toByteArray()
        val altitude = "altitude-bytes".toByteArray()
        val trip = entity(routeData = route, altitudeData = altitude)
        dao.insert(listOf(trip))

        val payloads = dao.payloads(trip.id)!!

        assertThat(payloads.routeData).isEqualTo(route)
        assertThat(payloads.altitudeData).isEqualTo(altitude)
    }

    @Test
    fun `payloads for a trip that is not there are null`() = runTest {
        assertThat(dao.payloads("no-such-id")).isNull()
    }

    /** An id-keyed re-insert (retried import, sync echo) is a no-op, not a duplicate. */
    @Test
    fun `re-inserting the same id does not duplicate the trip`() = runTest {
        val trip = entity()
        dao.insert(listOf(trip))

        dao.insert(listOf(trip.copy(distance = 99_999.0)))

        dao.observeScalars().test {
            val trips = awaitItem()
            assertThat(trips).hasSize(1)
            assertWithMessage("the original row wins — an ignore, not an upsert")
                .that(trips.single().distance).isWithin(0.5).of(10_000.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a trip removes its row and its payloads`() = runTest {
        val doomed = entity(routeData = "bytes".toByteArray())
        val survivor = entity(startDate = dateAtNoonUtc(2026, 7, 2), routeData = "kept".toByteArray())
        dao.insert(listOf(doomed, survivor))

        dao.delete(listOf(doomed.id))

        assertThat(dao.payloads(doomed.id)).isNull()
        assertThat(dao.payloads(survivor.id)!!.routeData).isEqualTo("kept".toByteArray())
    }

    @Test
    fun `existing ids reports every row`() = runTest {
        val first = entity(startDate = dateAtNoonUtc(2026, 7, 1))
        val second = entity(startDate = dateAtNoonUtc(2026, 7, 2))
        dao.insert(listOf(first, second))

        assertThat(dao.existingIds()).containsExactly(first.id, second.id)
    }
}
