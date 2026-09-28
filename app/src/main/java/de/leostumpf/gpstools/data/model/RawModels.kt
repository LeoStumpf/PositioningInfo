// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.data.model

import de.leostumpf.gpstools.domain.RawEpoch

/** Whether the chip delivers a raw data stream, as the platform reports it. */
enum class RawStreamStatus { UNKNOWN, READY, NOT_SUPPORTED, LOCATION_DISABLED }

/** One batch of raw measurements, with the extras the interference monitor does not need. */
data class RawMeasurementEpoch(
    val epoch: RawEpoch,
    /** Signals with a valid carrier-phase (accumulated delta range) measurement. */
    val carrierPhaseValid: Int,
    /** True once the receiver knows GNSS time absolutely (full bias), not just relative. */
    val hasFullBias: Boolean,
)

sealed interface RawMeasurementUpdate {
    data class Status(val status: RawStreamStatus) : RawMeasurementUpdate
    data class Epoch(val value: RawMeasurementEpoch) : RawMeasurementUpdate
}

/** One navigation-message frame as the satellite broadcast it. */
data class NavigationFrame(
    /** "GPS L1 C/A", "Galileo I/NAV", ... */
    val signal: String,
    val isGpsL1Ca: Boolean,
    val svid: Int,
    val data: ByteArray,
    val parityOk: Boolean,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

sealed interface NavigationUpdate {
    data class Status(val status: RawStreamStatus) : NavigationUpdate
    data class Frame(val value: NavigationFrame) : NavigationUpdate
}

data class PressureReading(val hPa: Float, val elapsedRealtimeMs: Long)

/** Where the top edge of the phone points, from the rotation-vector sensor. */
data class HeadingReading(
    val magneticAzimuthDegrees: Float,
    /** SensorManager accuracy constant: 3 high … 0 unreliable (figure-eight to calibrate). */
    val accuracy: Int,
)
