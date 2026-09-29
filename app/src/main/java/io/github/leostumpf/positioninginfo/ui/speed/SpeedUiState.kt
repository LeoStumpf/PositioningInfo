// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.speed

import io.github.leostumpf.positioninginfo.domain.FixFreshness
import io.github.leostumpf.positioninginfo.domain.SpeedSample
import io.github.leostumpf.positioninginfo.domain.SpeedUnit

/** Everything the speed screen draws, in already-decided form. */
data class SpeedUiState(
    val unit: SpeedUnit = SpeedUnit.DEFAULT,
    /** Current speed in m/s, or null when there is nothing trustworthy to show. */
    val speedMps: Float? = null,
    val maxMps: Double? = null,
    val averageMps: Double? = null,
    val horizontalAccuracyM: Float? = null,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val freshness: FixFreshness = FixFreshness.EXPIRED,
    val hasEverHadFix: Boolean = false,
    val gpsEnabled: Boolean = true,
    /** 68 % uncertainty of the current speed, in m/s, when the receiver reports one. */
    val speedAccuracyMps: Float? = null,
    /** The position comes from a mock-location app, not from the receiver. */
    val isMock: Boolean = false,
    /** Speed since the last reset, oldest first. */
    val speedHistory: List<SpeedSample> = emptyList(),
) {
    /** True while the receiver is still searching and there is genuinely nothing to report. */
    val isAcquiring: Boolean
        get() = gpsEnabled && speedMps == null && !hasEverHadFix
}
