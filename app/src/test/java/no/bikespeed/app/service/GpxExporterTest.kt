package no.bikespeed.app.service

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import no.bikespeed.app.model.RouteSample
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element

/**
 * The export is the app's only interoperability surface: whatever comes out of here has to be
 * readable by Strava, Komoot and Garmin, none of which are lenient. So these tests parse the
 * output back as XML rather than matching strings — a test that only greps for `<trkpt` would
 * happily pass on a document no importer could open.
 */
class GpxExporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `every route sample becomes a track point`() {
        val gpx = GpxExporter.makeGpx(route = makeRoute(count = 3), trackName = "Morning ride")

        val track = parse(gpx)

        assertThat(track.points).hasSize(3)
    }

    @Test
    fun `coordinates and elevations survive the round trip`() {
        val route = listOf(
            RouteSample(latitude = 59.9139, longitude = 10.7522, altitude = 100.0, timestamp = Instant.EPOCH),
            RouteSample(latitude = 59.9184, longitude = 10.7530, altitude = 142.5, timestamp = Instant.EPOCH),
        )

        val track = parse(GpxExporter.makeGpx(route = route, trackName = "Ride"))

        val first = track.points.first()
        assertThat(first.latitude).isWithin(0.000001).of(59.9139)
        assertThat(first.longitude).isWithin(0.000001).of(10.7522)
        assertThat(first.elevation!!).isWithin(0.05).of(100.0)
        assertThat(track.points.last().elevation!!).isWithin(0.05).of(142.5)
    }

    @Test
    fun `timestamps are iso 8601`() {
        val sample = RouteSample(
            latitude = 59.9139,
            longitude = 10.7522,
            altitude = 100.0,
            timestamp = Instant.ofEpochSecond(1_770_000_000),
        )

        val track = parse(GpxExporter.makeGpx(route = listOf(sample), trackName = "Ride"))

        val time = track.points.first().time!!
        assertThat(time).isEqualTo("2026-02-02T02:40:00Z")

        // The point of the format, rather than of the string: a GPX consumer has to be able
        // to read it back.
        val parsed = Instant.parse(time)
        assertThat(parsed.epochSecond).isEqualTo(1_770_000_000L)
    }

    /**
     * **The interoperability guard.** Coordinates must always use a `.` decimal separator.
     * Norwegian — the app's other language — writes decimals with a comma, so a formatter on
     * the current locale would emit `lat="59,9139000"`: not valid GPX, and silently
     * unreadable by every app the rider would want to open it in. The exporter passes
     * `Locale.ROOT` explicitly (Kotlin's bare `String.format` IS locale-aware, unlike Swift's);
     * this test runs the export under nb_NO to prove the pin holds.
     */
    @Test
    fun `coordinates use dot decimal separators even under a comma locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("nb-NO"))
        try {
            // Deliberately a name a Norwegian device would produce, comma and all: the rule
            // is about the *numbers*, and a blanket "no commas in the document" check would
            // both miss the point and fail on a perfectly good track name.
            val track = parse(GpxExporter.makeGpx(route = makeRoute(count = 2), trackName = "14. juli 2026, 10:02"))

            val decimal = Regex("^-?[0-9]+\\.[0-9]+$")
            for (point in track.points) {
                assertWithMessage("latitude '${point.rawLatitude}' is not a dot-separated decimal")
                    .that(point.rawLatitude.matches(decimal)).isTrue()
                assertWithMessage("longitude '${point.rawLongitude}' is not a dot-separated decimal")
                    .that(point.rawLongitude.matches(decimal)).isTrue()
            }
            assertThat(track.points).isNotEmpty()
        } finally {
            Locale.setDefault(previous)
        }
    }

    /**
     * A point recorded without usable vertical accuracy has no height — the tag is left out
     * rather than written as `0`, which would place the rider at sea level.
     */
    @Test
    fun `a sample without altitude omits the elevation tag`() {
        val sample = RouteSample(latitude = 59.9139, longitude = 10.7522, altitude = null, timestamp = Instant.EPOCH)

        val track = parse(GpxExporter.makeGpx(route = listOf(sample), trackName = "Ride"))

        assertThat(track.points).hasSize(1)
        assertThat(track.points.first().elevation).isNull()
    }

    /**
     * Nothing stops a caller asking to export a trip whose route never recorded. It has to
     * produce a valid, empty document — a malformed one is worse than an empty one, since
     * the rider only finds out once the import fails somewhere else.
     */
    @Test
    fun `an empty route produces a valid empty track`() {
        val track = parse(GpxExporter.makeGpx(route = emptyList(), trackName = "Ride"))

        assertThat(track.points).isEmpty()
    }

    /**
     * The track name reaches the file as markup, so it has to be escaped. A date is what
     * actually goes in there today, but the rule shouldn't depend on that staying true.
     */
    @Test
    fun `a track name containing markup is escaped`() {
        val gpx = GpxExporter.makeGpx(route = makeRoute(count = 1), trackName = "Tom & Jerry's <ride>")

        val track = parse(gpx) // would throw on unescaped `<`

        assertThat(track.name).isEqualTo("Tom & Jerry's <ride>")
    }

    @Test
    fun `writing produces a readable file named after the trip`() {
        val directory = folder.newFolder()

        val file = GpxExporter.write(
            route = makeRoute(count = 2),
            trackName = "Ride",
            fileName = "BikeSpeed-2026-07-04-0915",
            directory = directory,
        )

        assertThat(file.name).isEqualTo("BikeSpeed-2026-07-04-0915.gpx")
        assertThat(parse(file.readText()).points).hasSize(2)
    }

    /**
     * The one guard between a localized date and the filesystem. Its only production caller
     * passes an already-safe `yyyy-MM-dd-HHmm`, so nothing exercises it in practice — which
     * is precisely why it needs pinning here: the day someone swaps the file name for a
     * localized date, the separators have to be gone before they reach the path.
     */
    @Test
    fun `a file name carrying path separators is sanitized`() {
        val directory = folder.newFolder()

        // A Norwegian and an English localized date, in one name: `.` and `/` both.
        val file = GpxExporter.write(
            route = makeRoute(count = 1),
            trackName = "Ride",
            fileName = "14/07/2026 10.02",
            directory = directory,
        )

        assertWithMessage("a `/` in the name must not let the file escape its directory")
            .that(file.parentFile!!.canonicalFile).isEqualTo(directory.canonicalFile)
        assertThat(file.extension).isEqualTo("gpx")
        assertWithMessage("a `.` in the name would leave the file with a second extension")
            .that(file.nameWithoutExtension.contains(".")).isFalse()
        assertThat(parse(file.readText()).points).hasSize(1)
    }

    // region Helpers

    private fun makeRoute(count: Int): List<RouteSample> = (0 until count).map { index ->
        RouteSample(
            latitude = 59.9139 + index * 0.001,
            longitude = 10.7522,
            altitude = 100.0 + index,
            timestamp = Instant.ofEpochSecond(1_770_000_000L + index),
        )
    }

    private data class ParsedTrack(val name: String, val points: List<ParsedPoint>)

    private data class ParsedPoint(
        val rawLatitude: String,
        val rawLongitude: String,
        val elevation: Double?,
        val time: String?,
    ) {
        val latitude: Double get() = rawLatitude.toDouble()
        val longitude: Double get() = rawLongitude.toDouble()
    }

    /** Reads the document back the way an importer would — a real XML parse, not a grep. */
    private fun parse(gpx: String): ParsedTrack {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(gpx.toByteArray()))

        val name = document.getElementsByTagName("name").item(0)?.textContent ?: ""
        val pointNodes = document.getElementsByTagName("trkpt")
        val points = (0 until pointNodes.length).map { i ->
            val element = pointNodes.item(i) as Element
            ParsedPoint(
                rawLatitude = element.getAttribute("lat"),
                rawLongitude = element.getAttribute("lon"),
                elevation = element.getElementsByTagName("ele").item(0)?.textContent?.toDouble(),
                time = element.getElementsByTagName("time").item(0)?.textContent,
            )
        }
        return ParsedTrack(name = name, points = points)
    }

    // endregion
}
