package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Golden values for the haversine that stands in for the platform's distance function
 * everywhere in the model/service layers — if this drifts, distance accumulation and the
 * legacy speed-profile fallback drift with it.
 */
class GeoMathTest {

    @Test
    fun `zero distance between identical coordinates`() {
        assertThat(GeoMath.haversineMeters(59.9139, 10.7522, 59.9139, 10.7522))
            .isWithin(0.001).of(0.0)
    }

    @Test
    fun `one degree of latitude is about 111 kilometers`() {
        val d = GeoMath.haversineMeters(59.0, 10.0, 60.0, 10.0)
        // R * 1° in radians = 6371000 * π/180 ≈ 111.19 km
        assertThat(d).isWithin(200.0).of(111_195.0)
    }

    /** Oslo city hall → Oslo central station, a known ~1.1 km hop. */
    @Test
    fun `a short urban hop reads its known distance`() {
        val d = GeoMath.haversineMeters(59.9115, 10.7331, 59.9109, 10.7527)
        assertThat(d).isWithin(30.0).of(1_100.0)
    }

    /** The test-harness convention: metres north = latitude + meters / 111_320. */
    @Test
    fun `moving 50 meters north via the harness convention reads 50 meters`() {
        val d = GeoMath.haversineMeters(59.9139, 10.7522, 59.9139 + 50.0 / 111_320, 10.7522)
        assertThat(d).isWithin(0.5).of(50.0)
    }
}
