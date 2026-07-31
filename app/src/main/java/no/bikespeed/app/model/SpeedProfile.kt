package no.bikespeed.app.model

import java.time.Duration
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a recorded [RouteSample] track into the `List<SpeedSample>` a speed-over-distance
 * chart draws.
 *
 * Distance (the X-axis) comes from [RouteSample.distance] — the trip's guarded accumulated
 * distance, the *same* basis [AltitudeSample] uses — so the speed and height charts, and the
 * trip's distance total, all agree. Legacy trips didn't store it; there a haversine running
 * total stands in.
 *
 * Speed comes from two sources, in order of trust:
 * 1. [RouteSample.speed] — the live GPS (Doppler) reading captured per point (new trips).
 * 2. Position-derived — segment distance ÷ time between two points (older trips whose samples
 *    have `speed == null`).
 *
 * The derived path is inherently spiky (a metre of GPS wander over a second reads as a speed
 * jump), so a small centred moving average smooths the series. That smoothing is harmless on
 * the already-clean stored readings, so the same pass runs for both. A long ride is then
 * decimated to a chart-appropriate density.
 *
 * Pure on purpose: the detail view runs this off the main thread alongside the track decode,
 * keeping an O(samples) pass out of the frame budget.
 */
object SpeedProfile {

    /**
     * Mirrors `TripManager`'s teleport guard (120 km/h). A GPS jitter between two samples can
     * imply an absurd derived speed; clamp it so one bad point can't blow out the chart's Y-axis.
     */
    const val MAX_PLAUSIBLE_SPEED: Double = 120.0 / 3.6

    /**
     * Samples spanned by the smoothing window. Route points land roughly every 10 m, so five
     * points ≈ 40 m of riding — enough to tame position-derived jitter without flattening a
     * real acceleration.
     */
    const val SMOOTHING_WINDOW: Int = 5

    /**
     * Above this gap (seconds) between two samples, `segment ÷ dt` no longer reads as an
     * instantaneous speed — the interval spans a stop (a light, a manual pause), so its
     * distance was covered over riding time plus idle time. Deriving across it would draw a
     * phantom near-standstill; carry the last real speed instead. Only the derived (legacy)
     * path consults this — stored GPS speed is authoritative.
     */
    const val MAX_DERIVATION_GAP_SECONDS: Double = 60.0

    /**
     * Cap on plotted points. A phone-width chart can't resolve more, and it keeps a 40 km ride
     * (~4000 route samples) from rendering thousands of marks. Well above what the eye needs,
     * so the decimated line stays faithful.
     */
    const val MAX_CHART_POINTS: Int = 500

    /**
     * Builds the profile. Empty for a track too short to define a single segment — the chart
     * then shows its "no data" state, exactly as the height profile does for a trip without
     * altitude.
     */
    fun build(route: List<RouteSample>): List<SpeedSample> {
        if (route.size < 2) return emptyList()

        // (distance at this point, speed there). Distance is the stored accumulated distance
        // where present, and a haversine running total only for legacy trips that didn't
        // record it.
        val distances = DoubleArray(route.size)
        val speeds = DoubleArray(route.size)
        var derivedCumulative = 0.0

        for (index in route.indices) {
            val sample = route[index]

            val distance: Double
            val speed: Double
            if (index == 0) {
                distance = sample.distance ?: 0.0
                // No preceding segment yet; corrected below once the first segment's speed is known.
                speed = sample.speed?.let { max(0.0, it) } ?: 0.0
            } else {
                val previous = route[index - 1]
                val stored = sample.distance
                if (stored != null) {
                    distance = stored
                } else {
                    derivedCumulative += GeoMath.haversineMeters(
                        previous.latitude, previous.longitude,
                        sample.latitude, sample.longitude,
                    )
                    distance = derivedCumulative
                }

                val storedSpeed = sample.speed
                if (storedSpeed != null && storedSpeed >= 0) {
                    speed = storedSpeed
                } else {
                    val dt = Duration.between(previous.timestamp, sample.timestamp).toMillis() / 1000.0
                    val segment = distance - distances[index - 1]
                    speed = if (dt > 0 && dt <= MAX_DERIVATION_GAP_SECONDS) {
                        segment / dt
                    } else {
                        // dt <= 0 (bad timestamps) or a stop-sized gap: keep the last real
                        // speed rather than derive a misleading value across non-riding time.
                        speeds[index - 1]
                    }
                }
            }
            distances[index] = distance
            speeds[index] = min(speed, MAX_PLAUSIBLE_SPEED)
        }

        // The starting point had no segment to derive from; when it also had no stored reading
        // it reads 0, which would draw a false dip to a standstill on a ride that began
        // mid-motion. Lift it to the first real speed instead.
        if (route.first().speed == null && route.size >= 2) {
            speeds[0] = speeds[1]
        }

        return decimated(smoothed(distances, speeds))
    }

    /**
     * A centred moving average over [SMOOTHING_WINDOW] points. Centred (not trailing) so the
     * curve doesn't lag the ride; the window shrinks at the two ends rather than inventing
     * points beyond them.
     */
    private fun smoothed(distances: DoubleArray, speeds: DoubleArray): List<SpeedSample> {
        val half = SMOOTHING_WINDOW / 2
        return distances.indices.map { index ->
            val lower = max(0, index - half)
            val upper = min(distances.size - 1, index + half)
            var sum = 0.0
            for (i in lower..upper) sum += speeds[i]
            SpeedSample(distance = distances[index], speed = sum / (upper - lower + 1))
        }
    }

    /**
     * Thins an over-long series to at most [MAX_CHART_POINTS] by an even stride, always
     * keeping the final point so the chart still reaches the trip's full distance. Short
     * rides pass through untouched.
     */
    private fun decimated(samples: List<SpeedSample>): List<SpeedSample> {
        if (samples.size <= MAX_CHART_POINTS) return samples
        val stride = ceil(samples.size.toDouble() / MAX_CHART_POINTS).toInt()
        val result = samples.filterIndexed { index, _ -> index % stride == 0 }.toMutableList()
        val last = samples.last()
        if (result.last().distance != last.distance) {
            result.add(last)
        }
        return result
    }
}
