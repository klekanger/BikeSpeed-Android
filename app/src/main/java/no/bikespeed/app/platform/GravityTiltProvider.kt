package no.bikespeed.app.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A small, heavily-smoothed gravity vector that nudges the gauge's metallic gradients so the
 * bezel and face catch light as the phone moves — the port of the iOS MotionManager. Purely
 * cosmetic: low-pass filtered (α 0.18) and published only past a small threshold so it never
 * floods recomposition.
 */
class GravityTiltProvider(
    private val sensorManager: SensorManager,
) : SensorEventListener {

    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private var isStarted = false

    private var smoothedX = 0f
    private var smoothedY = 0f

    private val _tilt = MutableStateFlow(Offset.Zero)
    val tilt: StateFlow<Offset> = _tilt.asStateFlow()

    fun start() {
        val sensor = sensor ?: return
        if (isStarted) return
        isStarted = true
        // ~20 Hz, matching the iOS update interval.
        sensorManager.registerListener(this, sensor, 50_000)
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Normalize m/s² to the ±1 unit vector iOS gravity reports; flip Y so the
        // upright-portrait rest pose reads the same as iOS's (0, -1).
        val gx = event.values[0] / SensorManager.GRAVITY_EARTH
        val gy = -event.values[1] / SensorManager.GRAVITY_EARTH

        smoothedX += (gx - smoothedX) * SMOOTHING_FACTOR
        smoothedY += (gy - smoothedY) * SMOOTHING_FACTOR

        val current = _tilt.value
        if (abs(smoothedX - current.x) > PUBLISH_THRESHOLD || abs(smoothedY - current.y) > PUBLISH_THRESHOLD) {
            _tilt.value = Offset(smoothedX, smoothedY)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val SMOOTHING_FACTOR = 0.18f
        const val PUBLISH_THRESHOLD = 0.004f
    }
}
