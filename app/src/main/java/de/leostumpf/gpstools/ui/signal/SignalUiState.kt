// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.signal

import de.leostumpf.gpstools.data.model.AssistanceCapabilities
import de.leostumpf.gpstools.domain.Constellation
import de.leostumpf.gpstools.domain.Dop
import de.leostumpf.gpstools.domain.PositioningQuality
import de.leostumpf.gpstools.domain.ResolutionClass
import de.leostumpf.gpstools.domain.SbasSystem
import de.leostumpf.gpstools.domain.SignalBand

/** Everything the signals-and-accuracy screen draws. */
data class SignalUiState(
    val resolution: ResolutionClass = ResolutionClass.NO_FIX,
    /** Accuracy the receiver reports for the current fix — the measured figure, not an estimate. */
    val measuredAccuracyM: Float? = null,
    val bandsInUse: List<SignalBand> = emptyList(),
    val constellationsInUse: List<Constellation> = emptyList(),
    val sbasInView: List<SbasSystem> = emptyList(),
    val sbasUsedInFix: Boolean = false,
    val dualFrequency: Boolean = false,
    val bandsUnavailable: Boolean = false,
    val capabilities: AssistanceCapabilities = AssistanceCapabilities(),
    /** Geometry of the satellites in the fix, computed here from their directions. */
    val dop: Dop? = null,
    val dopSatellites: Int = 0,
    /** The chip's own figures from NMEA GSA, for comparison; null when not reported. */
    val chipPdop: Double? = null,
    val chipHdop: Double? = null,
    val chipVdop: Double? = null,
) {
    companion object {
        fun from(
            quality: PositioningQuality,
            measuredAccuracyM: Float?,
            capabilities: AssistanceCapabilities,
        ) = SignalUiState(
            resolution = quality.resolution,
            measuredAccuracyM = measuredAccuracyM,
            bandsInUse = quality.bandsInUse,
            constellationsInUse = quality.constellationsInUse,
            sbasInView = quality.sbasInView,
            sbasUsedInFix = quality.sbasUsedInFix,
            dualFrequency = quality.dualFrequency,
            bandsUnavailable = quality.bandsUnavailable,
            capabilities = capabilities,
        )
    }
}
