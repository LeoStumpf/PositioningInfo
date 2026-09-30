// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.signal

import io.github.leostumpf.positioninginfo.domain.Dop
import io.github.leostumpf.positioninginfo.domain.PositioningQuality
import io.github.leostumpf.positioninginfo.domain.ResolutionClass
import io.github.leostumpf.positioninginfo.domain.SbasSystem
import io.github.leostumpf.positioninginfo.domain.SignalBand

/** Everything the signals-and-accuracy screen draws. */
data class SignalUiState(
    val resolution: ResolutionClass = ResolutionClass.NO_FIX,
    /** Accuracy the receiver reports for the current fix — the measured figure, not an estimate. */
    val measuredAccuracyM: Float? = null,
    val bandsInUse: List<SignalBand> = emptyList(),
    val sbasInView: List<SbasSystem> = emptyList(),
    val sbasUsedInFix: Boolean = false,
    val dualFrequency: Boolean = false,
    val bandsUnavailable: Boolean = false,
    /** Geometry of the satellites in the fix, computed here from their directions. */
    val dop: Dop? = null,
    val dopSatellites: Int = 0,
    /** The chip's own figures from NMEA GSA, for comparison; null when not reported. */
    val chipPdop: Double? = null,
    val chipHdop: Double? = null,
    val chipVdop: Double? = null,
    /** The other uncertainties Android reports with each fix, all 68 % figures. */
    val verticalAccuracyM: Float? = null,
    val speedAccuracyMps: Float? = null,
    val bearingAccuracyDeg: Float? = null,
    /** How often fixes actually arrive. */
    val updateIntervalMs: Long? = null,
    /** 1σ uncertainty of the fix's timestamp. */
    val timeUncertaintyMs: Double? = null,
    val isMock: Boolean = false,
) {
    companion object {
        fun from(quality: PositioningQuality, measuredAccuracyM: Float?) = SignalUiState(
            resolution = quality.resolution,
            measuredAccuracyM = measuredAccuracyM,
            bandsInUse = quality.bandsInUse,
            sbasInView = quality.sbasInView,
            sbasUsedInFix = quality.sbasUsedInFix,
            dualFrequency = quality.dualFrequency,
            bandsUnavailable = quality.bandsUnavailable,
        )
    }
}
