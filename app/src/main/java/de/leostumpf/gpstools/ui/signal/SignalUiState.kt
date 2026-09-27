// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.signal

import de.leostumpf.gpstools.data.model.AssistanceCapabilities
import de.leostumpf.gpstools.domain.Constellation
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
