package no.bikespeed.app.data

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.bikespeed.app.model.AltitudeSample
import no.bikespeed.app.model.RouteSample
import no.bikespeed.app.model.TripLogEntry
import no.bikespeed.app.service.NoOpTripSyncEngine
import no.bikespeed.app.service.NowProvider
import no.bikespeed.app.service.TripSyncEngine

/**
 * Owns the store and every seam a Pro tier will reach through. One object injected app-wide,
 * so the control bar, the list and the detail screen each depend on exactly one thing.
 *
 * Note what is *not* here: any in-memory list or ordering the store must maintain. The trip
 * list observes the DAO's Flow — live, sorted by the database, and never loading a row it
 * doesn't draw.
 */
class TripDataStack(
    val repository: TripRepository,
    /**
     * [NoOpTripSyncEngine] today. Every mutation below already notifies it, so the day a real
     * engine appears, no call site changes. `TripDataStackTest` is what keeps that promise
     * honest.
     */
    val sync: TripSyncEngine = NoOpTripSyncEngine(),
    private val now: NowProvider = NowProvider { Instant.now() },
    private val encodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * The one place a trip is written, called from the control bar at Save.
     *
     * The encode is O(samples), off the main thread. **Throws rather than swallowing.**
     * `TripManager.reset()` throws away the only other copy, so a swallowed error would let
     * a full disk silently eat the ride while the app confirms it saved. The failure has to
     * reach the rider while the trip is still in memory to retry. Same for the encodes: a
     * track that fails to encode must not quietly persist a trip with no track.
     */
    suspend fun save(entry: TripLogEntry, altitudeProfile: List<AltitudeSample>, route: List<RouteSample>) {
        val (altitudeData, routeData) = withContext(encodeDispatcher) {
            // "An empty track is not a track": leave the column null so the detail view can
            // keep telling "no track recorded" apart from "a track of nothing".
            Pair(
                if (altitudeProfile.isEmpty()) null else TripPayloadCoder.encodeAltitudeProfile(altitudeProfile),
                if (route.isEmpty()) null else TripPayloadCoder.encodeRoute(route),
            )
        }

        repository.insert(
            listOf(
                TripEntity.from(
                    entry = entry,
                    altitudeData = altitudeData,
                    routeData = routeData,
                    updatedAt = now.now(),
                )
            )
        )

        sync.tripDidChange(entry.id)
    }

    /**
     * The one place a trip is removed — the swipe *and* the detail view's button, one path.
     *
     * Deleting the row takes its payload blobs with it: they are columns of the row, so there
     * is no second source of truth to fall out of step and no orphaned track to reap.
     *
     * **The soft-delete seam.** When sync ships, this becomes `deletedAt = now; updatedAt =
     * now` and nothing else changes, because the DAO's list query already filters
     * `deletedAt IS NULL`.
     *
     * The sync notification is *awaited* rather than fired-and-forgotten: fire-and-forget
     * would leave no way to observe the engine was told, and a test that has to sleep to
     * find out will eventually lie.
     */
    suspend fun delete(ids: List<UUID>) {
        repository.delete(ids)
        for (id in ids) {
            sync.tripWasDeleted(id)
        }
    }
}
