package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import org.junit.Test

/**
 * The speed profile is derived at view time from the recorded track, never stored. It has two
 * jobs: use the live GPS speed a new trip records per point, and fall back to deriving speed
 * from the timestamps for older trips whose samples carry no speed — so every trip with a
 * route gets a chart.
 */
class SpeedProfileTest {

    private val baseLatitude = 59.9139
    private val baseLongitude = 10.7522
    private val base: Instant = Instant.ofEpochSecond(1_760_000_000)

    /**
     * Builds a straight northbound track [count] points long, one point every [spacingMeters],
     * one second apart. [storedSpeed] is written onto every point when non-null (the new-trip
     * path); leaving it null exercises the derive-from-timestamps fallback. [storeDistance]
     * records the accumulated distance on each point (as `TripManager` does); leaving it off
     * exercises the legacy haversine fallback.
     */
    private fun track(
        count: Int,
        spacingMeters: Double,
        secondsApart: Double = 1.0,
        storedSpeed: Double? = null,
        storeDistance: Boolean = false,
    ): List<RouteSample> = (0 until count).map { index ->
        RouteSample(
            latitude = baseLatitude + (index * spacingMeters) / 111_320,
            longitude = baseLongitude,
            altitude = 100.0,
            timestamp = base.plusMillis((index * secondsApart * 1000).toLong()),
            speed = storedSpeed,
            distance = if (storeDistance) index * spacingMeters else null,
        )
    }

    @Test
    fun `a track too short for a segment yields nothing`() {
        assertThat(SpeedProfile.build(emptyList())).isEmpty()
        assertThat(SpeedProfile.build(track(count = 1, spacingMeters = 10.0))).isEmpty()
    }

    @Test
    fun `distance is cumulative and starts at zero`() {
        // 5 points, 10 m apart → cumulative distances 0, 10, 20, 30, 40.
        val profile = SpeedProfile.build(track(count = 5, spacingMeters = 10.0))

        assertThat(profile).hasSize(5)
        assertThat(profile.first().distance).isWithin(0.01).of(0.0)
        assertThat(profile.last().distance).isWithin(0.5).of(40.0)
    }

    @Test
    fun `stored distance is used verbatim as the x-axis`() {
        // A new trip records accumulated distance per point. The profile must plot those exact
        // values (the same basis the height profile uses) rather than re-deriving from
        // coordinates — so the X-axis matches the height chart and the trip's distance total.
        val profile = SpeedProfile.build(
            track(count = 5, spacingMeters = 10.0, storedSpeed = 8.0, storeDistance = true)
        )

        assertThat(profile).hasSize(5)
        assertThat(profile.first().distance).isWithin(0.0001).of(0.0)
        // Exact, not "within 0.5": these are the stored values, no geodesy in the path.
        assertThat(profile.last().distance).isWithin(0.0001).of(40.0)
    }

    @Test
    fun `a stop-sized gap does not draw a false standstill`() {
        // Legacy trip (no stored speed): the rider holds a steady 10 m/s, but between points 1
        // and 2 sits a 120 s stop covering only the usual 10 m. Deriving 10 m ÷ 121 s ≈ 0.08
        // m/s across that gap would draw a phantom standstill; the gap guard must carry the
        // real speed instead.
        val offsets = listOf(0.0, 1.0, 122.0, 123.0) // 120 s stop between index 1 and 2
        val samples = offsets.mapIndexed { index, offset ->
            RouteSample(
                latitude = baseLatitude + index * 10.0 / 111_320,
                longitude = baseLongitude,
                altitude = 100.0,
                timestamp = base.plusMillis((offset * 1000).toLong()),
            )
        }

        val profile = SpeedProfile.build(samples)

        for (sample in profile) {
            assertWithMessage("the pause gap must not derive a near-zero speed")
                .that(sample.speed).isGreaterThan(5.0)
        }
    }

    @Test
    fun `a long ride is decimated to a chart-appropriate density`() {
        // Far more route points than a chart can resolve must be thinned, and the last point
        // kept so the line still reaches the trip's full distance.
        val count = SpeedProfile.MAX_CHART_POINTS * 3
        val samples = track(count = count, spacingMeters = 10.0, storedSpeed = 7.0, storeDistance = true)

        val profile = SpeedProfile.build(samples)

        assertThat(profile.size).isAtMost(SpeedProfile.MAX_CHART_POINTS + 1)
        assertWithMessage("shouldn't over-thin")
            .that(profile.size).isGreaterThan(SpeedProfile.MAX_CHART_POINTS / 2)
        assertThat(profile.last().distance).isWithin(0.0001).of((count - 1) * 10.0)
    }

    @Test
    fun `stored speed is used when present`() {
        // A steady 8 m/s recorded on every point. Smoothing a constant series leaves it constant.
        val profile = SpeedProfile.build(track(count = 8, spacingMeters = 8.0, storedSpeed = 8.0))

        for (sample in profile) {
            assertThat(sample.speed).isWithin(0.01).of(8.0)
        }
    }

    @Test
    fun `speed is derived from timestamps when no stored speed`() {
        // 10 m every second with no stored speed → 10 m/s derived on every segment.
        val profile = SpeedProfile.build(track(count = 8, spacingMeters = 10.0, secondsApart = 1.0))

        // Middle of the series, away from window-edge shrinkage, reads the true speed.
        assertThat(profile[4].speed).isWithin(0.1).of(10.0)
    }

    @Test
    fun `the starting point does not dip to zero when derived`() {
        // Riding at a steady 10 m/s from the first point. With no stored speed the anchor
        // point has no segment to derive from; it must inherit the first real speed rather
        // than read a false standstill.
        val profile = SpeedProfile.build(track(count = 8, spacingMeters = 10.0))

        assertWithMessage("the start must not draw a phantom standstill")
            .that(profile.first().speed).isGreaterThan(5.0)
    }

    @Test
    fun `a derived speed spike is clamped to the teleport ceiling`() {
        // One point jumps 300 m in a single second — 1080 km/h, the kind of GPS glitch the
        // teleport guard rejects live. Derived speed must not carry it into the chart's Y-axis.
        val samples = track(count = 5, spacingMeters = 10.0).toMutableList()
        val glitch = samples[2]
        samples[2] = glitch.copy(latitude = glitch.latitude + 300.0 / 111_320)

        val profile = SpeedProfile.build(samples)

        for (sample in profile) {
            assertWithMessage("a glitch must not exceed the clamp")
                .that(sample.speed).isAtMost(SpeedProfile.MAX_PLAUSIBLE_SPEED + 0.01)
        }
    }

    @Test
    fun `smoothing tames alternating jitter`() {
        // Points alternate fast/slow segments (5 m then 15 m per second). The raw derived
        // speed would swing 5↔15 m/s; the smoothed series stays near the 10 m/s mean through
        // the middle.
        var cumulative = 0.0
        val samples = (0 until 10).map { index ->
            if (index > 0) cumulative += if (index % 2 == 0) 15.0 else 5.0
            RouteSample(
                latitude = baseLatitude + cumulative / 111_320,
                longitude = baseLongitude,
                altitude = 100.0,
                timestamp = base.plusSeconds(index.toLong()),
            )
        }

        val profile = SpeedProfile.build(samples)

        // A middle point stays close to the mean, well inside the raw 5↔15 swing.
        assertThat(profile[5].speed).isWithin(3.0).of(10.0)
    }
}
