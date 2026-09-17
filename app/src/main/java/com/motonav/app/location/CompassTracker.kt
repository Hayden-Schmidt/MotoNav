package com.motonav.app.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location

// Wraps the rotation-vector sensor + declination correction + wrap-safe smoothing behind one
// `onHeadingChanged` callback. Owned by RideSessionService so it lives and dies with the ride
// session, not a composable (MotoNav_TASK7_STAGED_PLAN.md stage 3 — "must keep flowing with the
// screen off"). GPS course-over-ground is the fallback when the sensor is absent or its accuracy
// drops, per stage 3 §4.2 — never the primary, since it produces nothing at a standstill.
class CompassTracker(
    context: Context,
    private val onHeadingChanged: (Float?) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Optimistic until proven otherwise — onAccuracyChanged downgrades this if the sensor reports
    // low/unreliable accuracy. No sensor at all (rotationSensor == null) means GPS is primary.
    private var sensorReliable = rotationSensor != null
    private var smoothedHeading: Float? = null
    private var lastLocation: Location? = null

    fun start() {
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Feed in fixes as they arrive so declination correction and the GPS fallback stay current. */
    fun onLocationChanged(location: Location) {
        lastLocation = location
        if (!sensorReliable) {
            emitFromGpsBearing(location)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!sensorReliable) return
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        // Standard remap for a phone mounted vertically (screen facing the rider), the normal
        // handlebar-mount orientation — not the flat-on-a-table orientation getOrientation assumes.
        val remapped = FloatArray(9)
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)
        val magneticAzimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat().let {
            if (it < 0f) it + 360f else it
        }
        val declination = lastLocation?.let {
            GeomagneticField(
                it.latitude.toFloat(),
                it.longitude.toFloat(),
                it.altitude.toFloat(),
                System.currentTimeMillis(),
            ).declination
        } ?: 0f
        val trueHeading = trueHeadingDeg(magneticAzimuthDeg, declination)
        smoothedHeading = smoothAngleDeg(smoothedHeading, trueHeading)
        onHeadingChanged(smoothedHeading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensorReliable = accuracy > SensorManager.SENSOR_STATUS_UNRELIABLE &&
            accuracy != SensorManager.SENSOR_STATUS_ACCURACY_LOW
        if (!sensorReliable) {
            smoothedHeading = null
            lastLocation?.let { emitFromGpsBearing(it) } ?: onHeadingChanged(null)
        }
    }

    private fun emitFromGpsBearing(location: Location) {
        if (rotationSensor == null && location.hasBearing()) {
            smoothedHeading = smoothAngleDeg(smoothedHeading, location.bearing)
            onHeadingChanged(smoothedHeading)
        } else if (rotationSensor == null) {
            // Neither source usable — hide the marker rather than freeze it (stage 3 §4.3).
            onHeadingChanged(null)
        }
    }
}
