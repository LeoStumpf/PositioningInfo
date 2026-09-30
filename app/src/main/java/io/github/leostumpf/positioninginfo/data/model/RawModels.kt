// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data.model

import io.github.leostumpf.positioninginfo.domain.Constellation
import io.github.leostumpf.positioninginfo.domain.RawEpoch

/** Whether the chip delivers a raw data stream, as the platform reports it. */
enum class RawStreamStatus { UNKNOWN, READY, NOT_SUPPORTED, LOCATION_DISABLED }

/** One batch of raw measurements, with the extras the interference monitor does not need. */
data class RawMeasurementEpoch(
    val epoch: RawEpoch,
    /** Signals with a valid carrier-phase (accumulated delta range) measurement. */
    val carrierPhaseValid: Int,
    /** True once the receiver knows GNSS time absolutely (full bias), not just relative. */
    val hasFullBias: Boolean,
    /** Every signal's raw values, for the per-satellite detail. */
    val measurements: List<SignalMeasurement> = emptyList(),
)

/** One signal as the raw-measurement API reports it. */
data class SignalMeasurement(
    val constellation: Constellation,
    val svid: Int,
    val carrierFrequencyHz: Double?,
    val cn0DbHz: Double,
    /** GnssMeasurement.state bit flags; see [io.github.leostumpf.positioninginfo.domain.AcquisitionStage]. */
    val state: Int,
    val pseudorangeRateMps: Double,
    val multipath: Boolean?,
    /** RINEX signal-code letter ("C", "Q", "X", …), Android 10+. */
    val codeType: String? = null,
    /** Signal strength at the chip's correlators, after the antenna and front end (Android 11+). */
    val basebandCn0DbHz: Double? = null,
    val snrDb: Double? = null,
    /** 1σ uncertainty of the satellite time the chip decoded. */
    val receivedSvTimeUncertaintyNs: Long? = null,
    /** GnssMeasurement.ADR_STATE_* bits; see [io.github.leostumpf.positioninginfo.domain.RawSignal.carrierPhase]. */
    val carrierPhaseState: Int = 0,
    /** This signal's delay relative to the receiver's reference signal (Android 11+). */
    val interSignalBiasNs: Double? = null,
)

/** What the raw-measurement stream delivers: a status change or a batch of measurements. */
sealed interface RawMeasurementUpdate {
    /** The platform's report of whether raw measurements are available. */
    data class Status(val status: RawStreamStatus) : RawMeasurementUpdate

    /** One batch of measurements. */
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

/** What the navigation-message stream delivers: a status change or a decoded frame. */
sealed interface NavigationUpdate {
    /** The platform's report of whether navigation messages are available. */
    data class Status(val status: RawStreamStatus) : NavigationUpdate

    /** One frame as broadcast by a satellite. */
    data class Frame(val value: NavigationFrame) : NavigationUpdate
}

/** One barometer reading: pressure in hPa, and when it was taken on the elapsed-realtime clock. */
data class PressureReading(val hPa: Float, val elapsedRealtimeMs: Long)

/** Where the top edge of the phone points, from the rotation-vector sensor. */
data class HeadingReading(
    val magneticAzimuthDegrees: Float,
    /** SensorManager accuracy constant: 3 high … 0 unreliable (figure-eight to calibrate). */
    val accuracy: Int,
)
