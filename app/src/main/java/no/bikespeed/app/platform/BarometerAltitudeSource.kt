package no.bikespeed.app.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.pow
import no.bikespeed.app.service.AltitudeSource

/**
 * The standard-atmosphere pressure→altitude conversion (the formula behind
 * `SensorManager.getAltitude`), extracted pure so it's JVM-testable.
 */
object PressureAltitude {
    const val STANDARD_ATMOSPHERE_HPA: Double = 1013.25

    fun metersFromPressure(hPa: Double): Double =
        44_330.0 * (1.0 - (hPa / STANDARD_ATMOSPHERE_HPA).pow(1.0 / 5.255))
}

/**
 * The barometer as an [AltitudeSource]. Android gives raw pressure, not iOS's ready-made
 * relative altitude — so the relative zero is a baseline captured at the first reading after
 * [startUpdates], and every reading reports its offset from that. Stopping clears both, so
 * the zero point is rebased on every restart (a reading from before the stop must never be
 * readable afterwards) — the contract `TripManager`'s resume-reanchor depends on.
 *
 * No runtime permission gates the pressure sensor on Android, so iOS's
 * "available-but-silent under denied Motion permission" failure mode collapses to plain
 * "no sensor".
 */
class BarometerAltitudeSource(
    private val sensorManager: SensorManager,
) : AltitudeSource, SensorEventListener {

    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    override val isAvailable: Boolean get() = sensor != null

    override var relativeAltitude: Double? = null
        private set

    private var baseline: Double? = null
    private var isUpdating = false

    override fun startUpdates() {
        val sensor = sensor ?: return
        if (isUpdating) return
        isUpdating = true
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun stopUpdates() {
        // Cleared unconditionally, ahead of the listener guard: a reading from before the
        // stop must never be readable afterwards, whether or not updates were running.
        baseline = null
        relativeAltitude = null
        if (!isUpdating) return
        isUpdating = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        onPressureReading(event.values[0].toDouble())
    }

    /** Split from the SensorEvent (which tests can't construct) so the baseline logic is testable. */
    internal fun onPressureReading(hPa: Double) {
        val altitude = PressureAltitude.metersFromPressure(hPa)
        val base = baseline ?: altitude.also { baseline = it }
        relativeAltitude = altitude - base
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
