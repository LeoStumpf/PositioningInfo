// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.data

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import io.github.leostumpf.gpstools.data.model.HeadingReading
import io.github.leostumpf.gpstools.data.model.PressureReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** The phone's own sensors: barometer for altitude, rotation vector for a compass. */
class SensorDataSource(context: Context) {

    private val sensorManager = context.applicationContext.getSystemService<SensorManager>()

    val hasBarometer: Boolean get() = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    val hasCompass: Boolean get() = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    fun pressure(): Flow<PressureReading> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(PressureReading(event.values[0], event.timestamp / 1_000_000L))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    /** Magnetic azimuth of the phone's top edge, for a phone held roughly flat. */
    fun heading(): Flow<HeadingReading> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        var accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val azimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
                trySend(HeadingReading(azimuth, accuracy))
            }

            override fun onAccuracyChanged(sensor: Sensor, newAccuracy: Int) {
                accuracy = newAccuracy
            }
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    companion object {
        /**
         * Magnetic declination (true minus magnetic north) from the World Magnetic Model the
         * platform ships with, so no download is needed.
         */
        fun declinationDegrees(latitude: Double, longitude: Double, altitudeM: Double, timeMs: Long): Float =
            GeomagneticField(latitude.toFloat(), longitude.toFloat(), altitudeM.toFloat(), timeMs).declination
    }
}
