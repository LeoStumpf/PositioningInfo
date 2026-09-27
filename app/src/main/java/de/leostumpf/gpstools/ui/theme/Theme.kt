// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Amber, used wherever a reading is degraded rather than simply absent. */
val WarnAmber = Color(0xFFFFB300)
val OkGreen = Color(0xFF4CAF50)
val ErrorRed = Color(0xFFFF6B6B)
val DimGrey = Color(0xFF616161)

// Pure black rather than Material's dark grey: the speedometer is meant to be glanced at in
// a car mount, where an OLED black background costs no light and no attention.
//
// The scheme is deliberately not tied to the system light/dark setting. A speedometer is
// black for the same reason a car's instrument cluster is, and following the system would
// also mean a light screen behind the black window background declared in the manifest.
private val SpeedometerScheme = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFF7FD1FF),
    onSurfaceVariant = Color(0xFF9E9E9E),
    error = ErrorRed,
)

@Composable
fun GpsToolsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpeedometerScheme,
        typography = GpsToolsTypography,
        content = content,
    )
}
