package no.bikespeed.app.service

import java.io.File
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import no.bikespeed.app.model.RouteSample

/**
 * Renders a recorded track as GPX 1.1 — the interchange format every other cycling app reads,
 * so a trip can be pushed to Strava or Komoot. Writing it is the whole feature: no import, no
 * sync, nothing to maintain against someone else's API.
 *
 * Pure and file-system-parameterized: callers inject the base directory (`cacheDir` in
 * production), so everything here runs as plain JVM tests. Rendering a few thousand `<trkpt>`
 * elements and writing them to disk must happen off the main thread — the caller's job.
 */
object GpxExporter {

    private val timestamps = DateTimeFormatter.ISO_INSTANT

    /**
     * **`Locale.ROOT` is load-bearing.** Kotlin's `String.format` without a locale uses the
     * device default, and on a comma-decimal locale (nb_NO, the app's other language) it
     * would write `lat="59,9139000"` — invalid GPX that every app would silently reject.
     * `GpxExporterTest` pins the separator; don't drop the locale argument.
     */
    private fun decimal(value: Double, places: Int): String =
        String.format(Locale.ROOT, "%.${places}f", value)

    /**
     * Builds the GPX document. An empty route yields a valid file with an empty track
     * segment — the caller may still want to share it, and a malformed file is worse than an
     * empty one.
     */
    fun makeGpx(route: List<RouteSample>, trackName: String): String {
        val points = route.joinToString(separator = "\n") { sample ->
            buildString {
                append("      <trkpt lat=\"${decimal(sample.latitude, 7)}\" lon=\"${decimal(sample.longitude, 7)}\">")
                // Omitted rather than defaulted: a point recorded without usable vertical
                // accuracy has no height, and <ele>0</ele> would assert the rider was at sea
                // level.
                sample.altitude?.let { append("\n        <ele>${decimal(it, 1)}</ele>") }
                // Truncated to whole seconds so the output never carries fractional seconds,
                // matching the iOS internet-date-time format exactly.
                append("\n        <time>${timestamps.format(sample.timestamp.truncatedTo(ChronoUnit.SECONDS))}</time>")
                append("\n      </trkpt>")
            }
        }

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<gpx version="1.1" creator="BikeSpeed" xmlns="http://www.topografix.com/GPX/1/1">
            |  <trk>
            |    <name>${escaped(trackName)}</name>
            |    <trkseg>
            |$points
            |    </trkseg>
            |  </trk>
            |</gpx>
        """.trimMargin()
    }

    /**
     * Exports get a directory of their own under the injected base (the cache dir in
     * production), so [clearExports] can drop the lot without touching anything else kept
     * there.
     */
    fun exportsDirectory(base: File): File = File(base, "GPXExports")

    /**
     * One directory per trip. The rider sees the *file* name in the share sheet, so it stays
     * a clean, sortable date — but a date to the minute isn't unique (a trip need only be
     * 5 s and 10 m to save, so two can start in the same minute), and two trips sharing one
     * path would hand the share sheet the wrong ride's track. The trip's id disambiguates
     * the directory instead of uglifying the file name.
     */
    fun exportsDirectory(base: File, tripId: UUID): File =
        File(exportsDirectory(base), tripId.toString())

    /**
     * Call once at launch. The cache dir is reaped only under storage pressure, so without
     * this every detail-view visit leaves another `.gpx` behind. Launch is safe: no share
     * sheet can still be reading a file.
     */
    fun clearExports(base: File) {
        exportsDirectory(base).deleteRecursively()
    }

    /** Writes the document somewhere the share intent can hand it to another app. */
    fun write(
        route: List<RouteSample>,
        trackName: String,
        fileName: String,
        directory: File,
    ): File {
        directory.mkdirs()
        val file = File(directory, "${sanitized(fileName)}.gpx")
        // Write-then-rename for atomicity, so a share sheet can never see a half-written file.
        val temp = File(directory, "${sanitized(fileName)}.gpx.tmp")
        temp.writeText(makeGpx(route, trackName))
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "could not move ${temp.name} into place" }
        }
        return file
    }

    /**
     * The track name is a date the app formatted itself, so this is belt-and-braces — but it
     * is user data flowing into markup, and the cost of being wrong is a file no importer
     * will parse. `&` first, or it would re-escape the other entities.
     */
    private fun escaped(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /**
     * A localized date can carry a `/` (en) or a `.` (nb), which would either break the path
     * or leave the file with a second extension.
     */
    private fun sanitized(fileName: String): String {
        val cleaned = fileName.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
            .joinToString("")
        return cleaned.ifEmpty { "route" }
    }
}
