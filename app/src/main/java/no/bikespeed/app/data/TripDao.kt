package no.bikespeed.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    /**
     * The live list, newest first, tombstones filtered — and **scalars only, never the
     * blobs**: the trip list must never pay for a track it does not draw.
     */
    @Query(
        """
        SELECT id, startDate, duration, distance, averageSpeed, maxSpeed, totalAscent, totalDescent
        FROM trips
        WHERE deletedAt IS NULL
        ORDER BY startDate DESC
        """
    )
    fun observeScalars(): Flow<List<TripScalars>>

    /** A trip's payload blobs, for the detail view. Bytes only; the decode is the caller's. */
    @Query("SELECT altitudeData, routeData FROM trips WHERE id = :id LIMIT 1")
    suspend fun payloads(id: String): TripPayloads?

    /**
     * Ignore on conflict: an id-keyed re-insert (a retried import, a sync echo) must be a
     * no-op, not a crash and not an overwrite of newer local data.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(trips: List<TripEntity>)

    @Query("DELETE FROM trips WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("SELECT id FROM trips")
    suspend fun existingIds(): List<String>
}
