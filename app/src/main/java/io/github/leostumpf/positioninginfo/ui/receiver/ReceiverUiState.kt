// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.receiver

import io.github.leostumpf.positioninginfo.data.model.AssistanceCapabilities
import io.github.leostumpf.positioninginfo.data.model.RawStreamStatus
import io.github.leostumpf.positioninginfo.domain.GpsNavState
import io.github.leostumpf.positioninginfo.domain.InterferenceAssessment
import io.github.leostumpf.positioninginfo.domain.NmeaState

/** Everything the receiver-internals page draws. */
data class ReceiverUiState(
    val nmea: NmeaState = NmeaState(),
    val rawStatus: RawStreamStatus = RawStreamStatus.UNKNOWN,
    val rawEpochs: Int = 0,
    val carrierPhaseValid: Int = 0,
    val hasFullBias: Boolean? = null,
    val assessment: InterferenceAssessment? = null,
    val navStatus: RawStreamStatus = RawStreamStatus.UNKNOWN,
    /** Frames received per signal type, e.g. "GPS L1 C/A" to 120. */
    val navFrames: Map<String, Int> = emptyMap(),
    val gps: GpsNavState = GpsNavState(),
    /** Current GPS week from the phone's clock, to resolve the broadcast 10-bit week. */
    val currentGpsWeek: Int? = null,
    /** How long the receiver has run without a single navigation frame arriving. */
    val navSilentMs: Long = 0,
    /** What the platform says the chip supports; static for the device. */
    val capabilities: AssistanceCapabilities = AssistanceCapabilities(),
)
