package no.bikespeed.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID
import no.bikespeed.app.model.TripLogEntry

/**
 * The Room row for a saved trip — the Android analogue of the iOS `StoredTrip` model.
 *
 * The scalar columns mirror [TripLogEntry] exactly (SI units throughout). The sync metadata
 * (`updatedAt`, `deletedAt`, `syncedAt`, `remoteId`) is written locally but read by nothing
 * yet: it stays dormant so enabling the future REST backend never needs a schema change —
 * `syncedAt == null || updatedAt > syncedAt` will mean "owes the server".
 *
 * Deviations from the iOS row, both deliberate:
 * - `id` IS the primary key. iOS couldn't have a unique constraint (a CloudKit limitation)
 *   and enforced dedup in app code; Room has no such constraint, so the database enforces it.
 * - `deletedAt` filtering is pre-wired into every DAO list query, so flipping the hard delete
 *   to a soft delete later touches only the delete path.
 *
 * The payload blobs are `ByteArray?` columns holding [TripPayloadCoder] JSON. **No list query
 * may select them** — a 40 km ride is thousands of samples, and the trip list must never pay
 * for a track it does not draw. That's what the [TripScalars] projection is for.
 */
@Entity(
    tableName = "trips",
    indices = [Index("startDate")],
)
data class TripEntity(
    @PrimaryKey val id: String,
    val startDate: Instant,
    /** Seconds, active duration only. */
    val duration: Double,
    /** Meters. */
    val distance: Double,
    /** m/s. */
    val averageSpeed: Double,
    /** m/s. */
    val maxSpeed: Double,
    /** Meters; null = unknown, which is not the same as 0. */
    val totalAscent: Double?,
    /** Meters, positive; null = unknown. */
    val totalDescent: Double?,
    /** Dirty clock for the future sync engine. */
    val updatedAt: Instant,
    /** Soft-delete tombstone; currently never written (delete is hard), but always filtered on. */
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null,
    val remoteId: String? = null,
    /** JSON `[AltitudeSample]` via [TripPayloadCoder]; null when the trip recorded none. */
    val altitudeData: ByteArray? = null,
    /** JSON `[RouteSample]` via [TripPayloadCoder]; null when the trip recorded none. */
    val routeData: ByteArray? = null,
) {
    companion object {
        fun from(
            entry: TripLogEntry,
            altitudeData: ByteArray?,
            routeData: ByteArray?,
            updatedAt: Instant,
        ): TripEntity = TripEntity(
            id = entry.id.toString(),
            startDate = entry.startDate,
            duration = entry.duration,
            distance = entry.distance,
            averageSpeed = entry.averageSpeed,
            maxSpeed = entry.maxSpeed,
            totalAscent = entry.totalAscent,
            totalDescent = entry.totalDescent,
            updatedAt = updatedAt,
            altitudeData = altitudeData,
            routeData = routeData,
        )
    }
}

/**
 * The scalar projection every list query returns — the Android answer to iOS's
 * external-storage blobs being faulted in only on property access: the columns simply aren't
 * selected, so the list never pays for a payload.
 */
data class TripScalars(
    val id: String,
    val startDate: Instant,
    val duration: Double,
    val distance: Double,
    val averageSpeed: Double,
    val maxSpeed: Double,
    val totalAscent: Double?,
    val totalDescent: Double?,
) {
    fun toEntry(): TripLogEntry = TripLogEntry(
        id = UUID.fromString(id),
        startDate = startDate,
        duration = duration,
        distance = distance,
        averageSpeed = averageSpeed,
        maxSpeed = maxSpeed,
        totalAscent = totalAscent,
        totalDescent = totalDescent,
    )
}

/** A trip's payload blobs, as bytes: the O(samples) decode is the caller's to schedule off-main. */
data class TripPayloads(
    val altitudeData: ByteArray?,
    val routeData: ByteArray?,
)
