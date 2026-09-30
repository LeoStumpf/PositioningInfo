// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.trip

import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import io.github.leostumpf.positioninginfo.domain.TripStats

/** Everything the trip page draws. */
data class TripUiState(
    val recording: Boolean = false,
    val stats: TripStats? = null,
    val unit: SpeedUnit = SpeedUnit.DEFAULT,
    /** Whether climb is measured with the barometer (smooth) or from GNSS heights (noisy). */
    val climbFromBarometer: Boolean = false,
    /** The outcome of the last action (export, delete, trip full), or null. */
    val message: String? = null,
    /** Heights along the track, oldest first, thinned to at most a few hundred points. */
    val elevationProfile: List<Double> = emptyList(),
)
