// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.leostumpf.positioninginfo.domain.Constellation
import io.github.leostumpf.positioninginfo.domain.DiagnosisInput

// Names the screens already use, now pointing at the tokens.

/**
 * Signal strength (C/N₀) as a colour, the same in the satellite list and the signal map:
 * strong from 35 dB-Hz (open sky), usable from 25 (strong enough to decode the satellite's
 * data, as the diagnosis counts it), weak below.
 */
fun signalColour(cn0DbHz: Float): Color = when {
    cn0DbHz >= STRONG_SIGNAL_DB_HZ -> Palette.Good
    cn0DbHz >= DiagnosisInput.STRONG_CN0 -> Palette.Degraded
    else -> Palette.Bad
}

/**
 * The same tiers as brightness, for areas filled with [signalColour]: strong is brightest,
 * so the map still reads for anyone who cannot tell its green from its red.
 */
fun signalAlpha(cn0DbHz: Float): Float = when {
    cn0DbHz >= STRONG_SIGNAL_DB_HZ -> 0.75f
    cn0DbHz >= DiagnosisInput.STRONG_CN0 -> 0.5f
    else -> 0.28f
}

const val STRONG_SIGNAL_DB_HZ = 35f

/** Each system's colour; always shown next to its letter (G07, E24), never on its own. */
fun Constellation.color(): Color = when (this) {
    Constellation.GPS -> Color(0xFF6FB7FF)
    Constellation.GLONASS -> Color(0xFFFF8F80)
    Constellation.GALILEO -> Color(0xFFF2C94C)
    Constellation.BEIDOU -> Color(0xFFB8A2FF)
    Constellation.QZSS -> Color(0xFF5CD6C8)
    Constellation.IRNSS -> Color(0xFFF595C8)
    Constellation.SBAS, Constellation.UNKNOWN -> Color(0xFFA0A0A0)
}

private val InstrumentScheme = darkColorScheme(
    background = Palette.Background,
    surface = Palette.Background,
    surfaceContainer = Palette.Sheet,
    surfaceContainerHigh = Palette.Sheet,
    surfaceContainerLow = Palette.Surface,
    onBackground = Palette.TextPrimary,
    onSurface = Palette.TextPrimary,
    onSurfaceVariant = Palette.TextSecondary,
    primary = Palette.TextPrimary,
    onPrimary = Palette.Background,
    secondary = Palette.TextSecondary,
    outline = Palette.Outline,
    outlineVariant = Palette.Hairline,
    error = Palette.Bad,
)

@Composable
fun PositioningInfoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InstrumentScheme,
        typography = PositioningInfoTypography,
        content = content,
    )
}
