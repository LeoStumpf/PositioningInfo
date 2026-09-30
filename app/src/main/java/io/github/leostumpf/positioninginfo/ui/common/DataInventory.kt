// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** What the app keeps, counted, so the user can see it before clearing it. */
data class DataInventory(
    val tripPoints: Int = 0,
    val firstFixEntries: Int = 0,
    val unitChanged: Boolean = false,
    val historySamples: Int = 0,
)
