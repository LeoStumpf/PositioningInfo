// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tabular figures. Without this the speed number visibly jitters sideways as digits change
 * width, which is the single most distracting thing a large live-updating number can do.
 */
private const val TABULAR = "tnum"

val SpeedDisplayStyle = TextStyle(
    fontSize = 108.sp,
    lineHeight = 116.sp,
    fontWeight = FontWeight.Light,
    fontFeatureSettings = TABULAR,
)

val StatValueStyle = TextStyle(
    fontSize = 20.sp,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = TABULAR,
)

val StatusLineStyle = TextStyle(
    fontSize = 13.sp,
    fontWeight = FontWeight.Normal,
    fontFeatureSettings = TABULAR,
)

val GpsToolsTypography = Typography()
