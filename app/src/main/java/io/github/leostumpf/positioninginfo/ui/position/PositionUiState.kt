// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.position

import io.github.leostumpf.positioninginfo.domain.ScatterStats

/** Everything the position page draws. */
data class PositionUiState(
    val hasFix: Boolean = false,
    /** The position comes from a mock-location app. */
    val isMock: Boolean = false,
    val fixAgeMs: Long? = null,
    val horizontalAccuracyM: Float? = null,
    val coordinates: List<Pair<String, String>> = emptyList(),

    val gnssMslM: Double? = null,
    /** Where the sea-level height came from: the chip's NMEA, Android, or a geoid offset. */
    val mslSource: String? = null,
    val gnssEllipsoidM: Double? = null,
    val verticalAccuracyM: Float? = null,
    /** Geoid height N: ellipsoid minus sea level. */
    val geoidHeightM: Double? = null,

    val hasBarometer: Boolean = true,
    val pressureHpa: Double? = null,
    val baroStandardM: Double? = null,
    val baroCalibratedM: Double? = null,
    val seaLevelPressureHpa: Double? = null,
    val verticalSpeedMps: Double? = null,
    val calibrationSamples: Int = 0,

    val scatterRunning: Boolean = false,
    val scatter: ScatterStats? = null,
)
