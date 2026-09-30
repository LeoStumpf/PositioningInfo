// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Barometric altitude, calibrated against GNSS.
 *
 * A barometer resolves altitude changes of well under a metre and reacts instantly, but its
 * absolute altitude depends on the weather's sea-level pressure. GNSS altitude is absolute
 * but noisy. Combining them gives the best of both: every GNSS altitude with a decent
 * vertical accuracy implies a sea-level pressure, and an inverse-variance-weighted average of
 * those becomes the barometer's reference. Old calibration samples slowly lose weight
 * (time constant [CALIBRATION_DECAY_MS]) so the reference follows the weather.
 *
 * Vertical speed is computed from the smoothed pressure history, converted with the
 * *current* reference, so a calibration update never shows up as a fake climb.
 *
 * Immutable: every event returns a new instance.
 */
data class BaroAltimeter(
    /** Smoothed pressure (EMA, time constant [SMOOTHING_MS]). */
    val pressureHpa: Double? = null,
    val calibrationSamples: Int = 0,
    private val lastPressureAtMs: Long? = null,
    private val calibrationWeight: Double = 0.0,
    private val calibrationWeightedSum: Double = 0.0,
    private val lastCalibrationAtMs: Long? = null,
    /** Smoothed pressures of the last [VERTICAL_SPEED_WINDOW_MS], oldest first. */
    private val history: List<Pair<Long, Double>> = emptyList(),
) {
    /** Altitude in the ISA standard atmosphere (1013.25 hPa at sea level). */
    val standardAltitudeM: Double?
        get() = pressureHpa?.let { altitudeM(it, STANDARD_PRESSURE_HPA) }

    /** Calibrated reference sea-level pressure (QNH-like); null until calibrated. */
    val seaLevelPressureHpa: Double?
        get() = if (calibrationWeight > 0.0) calibrationWeightedSum / calibrationWeight else null

    val calibratedAltitudeM: Double?
        get() {
            val p = pressureHpa ?: return null
            val p0 = seaLevelPressureHpa ?: return null
            return altitudeM(p, p0)
        }

    /** Least-squares slope of the calibrated-or-standard altitude over the recent window. */
    val verticalSpeedMps: Double?
        get() {
            if (history.size < 3) return null
            val p0 = seaLevelPressureHpa ?: STANDARD_PRESSURE_HPA
            val t0 = history.first().first
            val xs = history.map { (it.first - t0) / 1000.0 }
            if (xs.last() < MIN_VERTICAL_SPEED_SPAN_S) return null
            val ys = history.map { altitudeM(it.second, p0) }
            val mx = xs.average()
            val my = ys.average()
            var sxy = 0.0
            var sxx = 0.0
            for (i in xs.indices) {
                sxy += (xs[i] - mx) * (ys[i] - my)
                sxx += (xs[i] - mx) * (xs[i] - mx)
            }
            return if (sxx > 0.0) sxy / sxx else null
        }

    fun onPressure(hPa: Float, atMs: Long): BaroAltimeter {
        if (!hPa.isFinite() || hPa <= 0f) return this
        val previous = pressureHpa
        val previousAt = lastPressureAtMs
        val smoothed = if (previous == null || previousAt == null) {
            hPa.toDouble()
        } else {
            val dt = atMs - previousAt
            // A clock going backwards (or a duplicate) carries no information.
            if (dt <= 0L) return this
            val alpha = 1.0 - exp(-dt / SMOOTHING_MS)
            previous + alpha * (hPa - previous)
        }
        val kept = history.filter { atMs - it.first <= VERTICAL_SPEED_WINDOW_MS }
        return copy(
            pressureHpa = smoothed,
            lastPressureAtMs = atMs,
            history = kept + (atMs to smoothed),
        )
    }

    fun onGnssAltitude(mslM: Double, verticalAccuracyM: Float?, atMs: Long): BaroAltimeter {
        val p = pressureHpa ?: return this
        if (verticalAccuracyM == null || verticalAccuracyM.isNaN() ||
            verticalAccuracyM > MAX_VERTICAL_ACCURACY_M || !mslM.isFinite()
        ) {
            return this
        }

        val implied = seaLevelPressureHpa(p, mslM)
        // Beyond the formula's range (≥ 44 330 m) it gives NaN, which would spoil the sum for good.
        if (!implied.isFinite()) return this
        // Guard against a receiver claiming 0 m, which would take all the weight forever.
        val sigma = max(verticalAccuracyM.toDouble(), MIN_VERTICAL_ACCURACY_M)
        val weight = 1.0 / (sigma * sigma)
        val previousAt = lastCalibrationAtMs
        val decay = if (previousAt == null) 1.0 else exp(-max(0L, atMs - previousAt) / CALIBRATION_DECAY_MS)

        return copy(
            calibrationSamples = calibrationSamples + 1,
            calibrationWeight = calibrationWeight * decay + weight,
            calibrationWeightedSum = calibrationWeightedSum * decay + weight * implied,
            lastCalibrationAtMs = if (previousAt == null) atMs else max(previousAt, atMs),
        )
    }

    companion object {
        const val STANDARD_PRESSURE_HPA = 1013.25
        const val SMOOTHING_MS = 2_000.0
        /** Calibration weights decay with this time constant (~10 min) to follow the weather. */
        const val CALIBRATION_DECAY_MS = 600_000.0
        const val MAX_VERTICAL_ACCURACY_M = 10f
        const val VERTICAL_SPEED_WINDOW_MS = 6_000L
        private const val MIN_VERTICAL_ACCURACY_M = 0.5
        private const val MIN_VERTICAL_SPEED_SPAN_S = 1.0

        /** International barometric formula: h = 44330 · (1 − (p/p0)^(1/5.255)). */
        fun altitudeM(pressureHpa: Double, seaLevelHpa: Double): Double =
            44_330.0 * (1.0 - (pressureHpa / seaLevelHpa).pow(1.0 / 5.255))

        /** Inverse of [altitudeM]: the sea-level pressure that puts [pressureHpa] at [altitudeM]. */
        fun seaLevelPressureHpa(pressureHpa: Double, altitudeM: Double): Double =
            pressureHpa / (1.0 - altitudeM / 44_330.0).pow(5.255)
    }
}
