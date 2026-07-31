package no.bikespeed.app.service

import java.util.UUID

/**
 * The seam a Pro/Plus tier reaches through.
 *
 * Nothing implements it today except the no-op. Its job is to ensure every mutation in
 * `TripDataStack` already *has* somewhere to announce itself, so shipping sync is adding a
 * type rather than editing every call site — and `TripDataStackTest` fails if a new mutation
 * path forgets to.
 *
 * This is the same seam the iOS app carries for its future non-CloudKit REST backend; the
 * dormant sync metadata on the Room row (`updatedAt`, `deletedAt`, `syncedAt`, `remoteId`)
 * is what a real implementation will read.
 */
interface TripSyncEngine {
    /** A trip was created or edited locally. */
    suspend fun tripDidChange(id: UUID)

    /** A trip was deleted locally. Takes the id, not the row: a deleted row has nothing left to read. */
    suspend fun tripWasDeleted(id: UUID)

    /** Pull remote changes into the local store. */
    suspend fun pullChanges()
}

/**
 * The free tier — and for now, everyone. Sync is a Pro feature; for everybody else the local
 * store *is* the truth, so there is nothing to tell.
 */
class NoOpTripSyncEngine : TripSyncEngine {
    override suspend fun tripDidChange(id: UUID) {}
    override suspend fun tripWasDeleted(id: UUID) {}
    override suspend fun pullChanges() {}
}
