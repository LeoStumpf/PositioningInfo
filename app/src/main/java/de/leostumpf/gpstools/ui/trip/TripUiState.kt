// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.trip

import de.leostumpf.gpstools.domain.SpeedUnit
import de.leostumpf.gpstools.domain.TripStats

data class TripUiState(
    val recording: Boolean = false,
    val stats: TripStats? = null,
    val unit: SpeedUnit = SpeedUnit.DEFAULT,
    /** Whether climb is measured with the barometer (smooth) or from GNSS heights (noisy). */
    val climbFromBarometer: Boolean = false,
    val message: String? = null,
)
