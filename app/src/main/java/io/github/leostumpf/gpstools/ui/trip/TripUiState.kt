// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.ui.trip

import io.github.leostumpf.gpstools.domain.SpeedUnit
import io.github.leostumpf.gpstools.domain.TripStats

data class TripUiState(
    val recording: Boolean = false,
    val stats: TripStats? = null,
    val unit: SpeedUnit = SpeedUnit.DEFAULT,
    /** Whether climb is measured with the barometer (smooth) or from GNSS heights (noisy). */
    val climbFromBarometer: Boolean = false,
    val message: String? = null,
    /** Heights along the track, oldest first, thinned to at most a few hundred points. */
    val elevationProfile: List<Double> = emptyList(),
)
