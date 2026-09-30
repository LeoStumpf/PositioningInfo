// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui

import android.hardware.SensorManager
import io.github.leostumpf.positioninginfo.data.SensorDataSource
import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.HeadingReading
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.domain.CompassTrust
import io.github.leostumpf.positioninginfo.domain.ObstructionMap
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.sky.SkyUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The sky view's own tools: compass mode (heading, declination, whether the compass can be
 * trusted), the signal map, and whether satellite paths are drawn.
 *
 * @param onChanged called when something only the sky view shows has changed.
 */
class SkyAnalysis internal constructor(
    private val scope: CoroutineScope,
    private val sensors: SensorDataSource,
    private val onChanged: () -> Unit,
) {
    private var compassMode = false
    private var mapMode = false
    private var showPaths = true
    private var obstruction = ObstructionMap()

    private var heading: HeadingReading? = null

    /** Smoothed magnetometer field strength, while compass mode runs. */
    private var magneticUt: Double? = null
    private var headingJob: Job? = null

    /** The World Magnetic Model barely changes over a few kilometres; see [geomagneticAt]. */
    private var geomagnetic: Geomagnetic? = null

    /** Tracking runs and the app is on screen: only then do the compass sensors run. */
    private var active = false

    /** Turns compass mode on or off; the compass sensors run only while it is on. */
    fun toggleCompass() {
        compassMode = !compassMode
        updateSensors()
        onChanged()
    }

    /** Shows or hides the satellites' paths across the sky. */
    fun toggleShowPaths() {
        showPaths = !showPaths
        onChanged()
    }

    /** Shows or hides the signal map; it keeps collecting either way. */
    fun toggleMap() {
        mapMode = !mapMode
        onChanged()
    }

    /** Adds the snapshot's signal strengths to the signal map. */
    internal fun onSnapshot(snapshot: GnssSnapshot) {
        obstruction = obstruction.onSnapshot(snapshot.satellites)
    }

    internal fun clearMap() {
        obstruction = ObstructionMap()
    }

    /** Adds the compass, heading line and signal map to the sky view built from the satellites. */
    internal fun decorate(state: SkyUiState, fix: SpeedFix?): SkyUiState {
        val field = fix?.let(::geomagneticAt)
        val declination = field?.declinationDegrees
        val reading = heading
        val trueHeading = reading?.let { normaliseDegrees(it.magneticAzimuthDegrees + (declination ?: 0f)) }
        val compass = compassMode
        return state.copy(
            magneticUt = magneticUt.takeIf { compass },
            compassTrust = magneticUt?.let { m -> field?.fieldUt?.let { CompassTrust(m, it) } }?.takeIf { compass },
            compassMode = compass,
            mapMode = mapMode,
            showPaths = showPaths,
            headingDegrees = trueHeading.takeIf { compass },
            headingText = headingText(fix, trueHeading, declination),
            compassUnreliable = compass && reading != null &&
                reading.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
            obstruction = if (mapMode) obstruction.cells else emptyList(),
            obstructionSamples = obstruction.totalSamples,
        )
    }

    /**
     * "Heading 87° true · declination 3.1° E · course 92°": the compass heading in compass
     * mode, the course over ground while moving, and the declination whenever either is shown.
     */
    private fun headingText(fix: SpeedFix?, trueHeading: Float?, declination: Float?): String? {
        val course = fix?.bearingDegrees?.takeIf { (fix.speedMps ?: 0f) > MOVING_MPS }
        val parts = mutableListOf<String>()
        if (compassMode && trueHeading != null) {
            val north = if (declination != null) "true" else "magnetic"
            parts += "Heading ${trueHeading.roundToInt()}° $north"
        }
        if (declination != null && (compassMode || course != null)) {
            parts += "declination ${abs(declination).fmt(1)}° ${if (declination >= 0) "E" else "W"}"
        }
        if (course != null) parts += "course ${course.roundToInt()}°"
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /** Whether tracking runs and the app is on screen; the compass sensors run only then. */
    internal fun setActive(value: Boolean) {
        active = value
        updateSensors()
    }

    /** Runs the compass sensors while [active] and in compass mode, and stops them otherwise. */
    private fun updateSensors() {
        val wanted = compassMode && active && sensors.hasCompass
        if (wanted && headingJob == null) {
            headingJob = scope.launch {
                launch { sensors.heading().collect(::onHeading) }
                launch {
                    // Smoothed, because the raw magnitude jitters by a few µT sample to sample.
                    sensors.magneticFieldUt().collect { ut ->
                        magneticUt = magneticUt?.let { it + (ut - it) * FIELD_SMOOTHING } ?: ut
                    }
                }
            }
        } else if (!wanted) {
            headingJob?.cancel()
            headingJob = null
            heading = null
            magneticUt = null
        }
    }

    /** Redraws only when the heading moved by a degree or its accuracy changed. */
    private fun onHeading(reading: HeadingReading) {
        val previous = heading
        heading = reading
        if (previous == null ||
            abs(previous.magneticAzimuthDegrees - reading.magneticAzimuthDegrees) >= 1f ||
            previous.accuracy != reading.accuracy
        ) {
            onChanged()
        }
    }

    /** The magnetic model at the fix, re-evaluated only after moving about 10 km. */
    private class Geomagnetic(
        val latitude: Double,
        val longitude: Double,
        val declinationDegrees: Float,
        val fieldUt: Double,
    )

    private fun geomagneticAt(fix: SpeedFix): Geomagnetic? {
        val lat = fix.latitude ?: return null
        val lon = fix.longitude ?: return null
        val cached = geomagnetic
        if (cached != null && abs(cached.latitude - lat) < SAME_FIELD_DEG &&
            abs(cached.longitude - lon) < SAME_FIELD_DEG
        ) {
            return cached
        }
        val alt = fix.ellipsoidAltitudeM ?: 0.0
        val now = System.currentTimeMillis()
        return Geomagnetic(
            latitude = lat,
            longitude = lon,
            declinationDegrees = SensorDataSource.declinationDegrees(lat, lon, alt, now),
            fieldUt = SensorDataSource.expectedFieldUt(lat, lon, alt, now),
        ).also { geomagnetic = it }
    }

    private fun normaliseDegrees(degrees: Float) = (degrees % FULL_TURN_DEG + FULL_TURN_DEG) % FULL_TURN_DEG

    private companion object {
        /** About 10 km: the magnetic model changes far less than the compass can show over that. */
        const val SAME_FIELD_DEG = 0.1
        const val FIELD_SMOOTHING = 0.1
        const val FULL_TURN_DEG = 360f

        /** Below walking pace the GNSS course is noise. */
        const val MOVING_MPS = 1f
    }
}
