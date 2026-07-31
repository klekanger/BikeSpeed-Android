package no.bikespeed.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The off-main-thread door into the store, deliberately narrow: the live scalar list, the
 * payload read (file read + O(samples) decode belong off the main thread), and the id-keyed
 * writes `TripDataStack` drives. Only value types cross this boundary.
 */
class TripRepository(
    private val dao: TripDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** The live list, newest first, scalars only. Room emits on its own query executor. */
    fun observeScalars(): Flow<List<TripScalars>> = dao.observeScalars()

    suspend fun payloads(id: UUID): TripPayloads = withContext(io) {
        dao.payloads(id.toString()) ?: TripPayloads(altitudeData = null, routeData = null)
    }

    suspend fun insert(trips: List<TripEntity>) = withContext(io) {
        dao.insert(trips)
    }

    suspend fun delete(ids: List<UUID>) = withContext(io) {
        dao.delete(ids.map { it.toString() })
    }

    suspend fun existingIds(): Set<UUID> = withContext(io) {
        dao.existingIds().map { UUID.fromString(it) }.toSet()
    }
}
