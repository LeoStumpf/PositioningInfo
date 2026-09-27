// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.domain

/**
 * Turns a raw GNSS speed into the value that should actually be shown.
 *
 * A stationary receiver does not report exactly zero: Doppler noise leaves roughly
 * 0.3-1 m/s of residual. Displaying that unfiltered is what makes cheap speedometers read
 * "3 km/h" while parked, so anything the receiver cannot distinguish from standing still
 * is clamped to zero.
 */
object SpeedFilter {

    /** Used when the receiver reports no speed accuracy of its own (below API 26 data). */
    const val fallbackNoiseFloorMps: Float = 0.5f

    /** Never treat an implausibly large accuracy figure as licence to zero real motion. */
    const val maxNoiseFloorMps: Float = 2.0f

    fun apply(speedMps: Float, speedAccuracyMps: Float?): Float {
        val floor = (speedAccuracyMps ?: fallbackNoiseFloorMps)
            .coerceIn(fallbackNoiseFloorMps, maxNoiseFloorMps)
        return if (speedMps < floor) 0f else speedMps
    }
}
