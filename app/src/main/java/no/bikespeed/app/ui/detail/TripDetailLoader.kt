package no.bikespeed.app.ui.detail

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.bikespeed.app.data.TripPayloadCoder
import no.bikespeed.app.data.TripRepository
import no.bikespeed.app.model.AltitudeSample
import no.bikespeed.app.model.Coordinate
import no.bikespeed.app.model.SpeedProfile
import no.bikespeed.app.model.SpeedSample
import no.bikespeed.app.model.TripLogEntry
import no.bikespeed.app.service.GpxExporter

/**
 * Loads a trip's payload blobs, decodes them, derives the speed profile, and writes the GPX —
 * all off the main thread, in one hop, the analogue of the iOS `Task.detached` load. The
 * result is computed **once**; nothing here is recomputed per render.
 */
class TripDetailLoader(
    private val repository: TripRepository,
    private val cacheDir: File,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class Loaded(
        /** Empty for a trip recorded without usable altitude. */
        val profile: List<AltitudeSample>,
        /**
         * Derived from the track (not stored), so it's present for any trip with a route —
         * live per-point speed on new trips, timestamp-derived on older ones.
         */
        val speedProfile: List<SpeedSample>,
        /** Empty for a trip with no track. */
        val coordinates: List<Coordinate>,
        /**
         * The GPX backing the share action. Written at load, not at tap time, so the share
         * intent always has a file that exists.
         */
        val gpxFile: File?,
    )

    suspend fun load(entry: TripLogEntry, trackName: String, fileName: String): Loaded =
        withContext(decodeDispatcher) {
            val payloads = repository.payloads(entry.id)

            val profile = payloads.altitudeData
                ?.let { runCatching { TripPayloadCoder.decodeAltitudeProfile(it) }.getOrNull() }
                ?: emptyList()

            val route = payloads.routeData
                ?.let { runCatching { TripPayloadCoder.decodeRoute(it) }.getOrNull() }
                ?: emptyList()

            if (route.isEmpty()) {
                return@withContext Loaded(
                    profile = profile,
                    speedProfile = emptyList(),
                    coordinates = emptyList(),
                    gpxFile = null,
                )
            }

            Loaded(
                profile = profile,
                speedProfile = SpeedProfile.build(route),
                coordinates = route.map { Coordinate(it.latitude, it.longitude) },
                gpxFile = runCatching {
                    GpxExporter.write(
                        route = route,
                        trackName = trackName,
                        fileName = fileName,
                        directory = GpxExporter.exportsDirectory(cacheDir, entry.id),
                    )
                }.getOrNull(),
            )
        }
}
