package no.bikespeed.app.platform

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import no.bikespeed.app.service.LocationManager

/**
 * The compass: rotation-vector azimuth fed into [LocationManager.processHeading], with
 * magnetic declination resolved from the last known coordinate (the analogue of
 * CLHeading's trueHeading, which is likewise only available once a fix exists).
 *
 * Never shows any calibration UI — a "wave your phone in a figure of eight" prompt on a
 * phone clamped to the bars of a moving bike isn't something the rider can act on.
 */
class HeadingProvider(
    private val sensorManager: SensorManager,
    private val locationManager: LocationManager,
) : SensorEventListener {

    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var isStarted = false
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        val sensor = sensor ?: return // no magnetometer (an emulator): heading stays null
        if (isStarted) return
        isStarted = true
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthDegrees = Math.toDegrees(orientation[0].toDouble())
        val magneticHeading = (azimuthDegrees % 360.0 + 360.0) % 360.0

        // Declination needs a position; until one exists trueHeading is "unknown" (negative),
        // and the pipeline falls back to the magnetic reading — same handover as iOS.
        val coordinate = locationManager.coordinate
        val trueHeading = if (coordinate != null) {
            val declination = GeomagneticField(
                coordinate.latitude.toFloat(),
                coordinate.longitude.toFloat(),
                (locationManager.altitude ?: 0.0).toFloat(),
                System.currentTimeMillis(),
            ).declination
            (magneticHeading + declination + 360.0) % 360.0
        } else {
            -1.0
        }

        locationManager.processHeading(
            trueHeading = trueHeading,
            magneticHeading = magneticHeading,
            accuracy = accuracyDegrees(event.accuracy),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * The sensor framework reports accuracy as coarse states, not degrees; map them onto the
     * degree scale the pipeline filters on. Unreliable → negative, which the pipeline drops —
     * magnetometer interference beside a bike frame is not rare.
     */
    private fun accuracyDegrees(status: Int): Double = when (status) {
        SensorManager.SENSOR_STATUS_UNRELIABLE -> -1.0
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 45.0
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 30.0
        else -> 15.0
    }
}
