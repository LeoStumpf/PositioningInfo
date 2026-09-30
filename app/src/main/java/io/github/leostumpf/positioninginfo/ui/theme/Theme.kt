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
    cn0DbHz >= STRONG_SIGNAL_DB_HZ -> STRONG_ALPHA
    cn0DbHz >= DiagnosisInput.STRONG_CN0 -> USABLE_ALPHA
    else -> WEAK_ALPHA
}

private const val STRONG_ALPHA = 0.75f
private const val USABLE_ALPHA = 0.5f
private const val WEAK_ALPHA = 0.28f

const val STRONG_SIGNAL_DB_HZ = 35f

/** Each system's colour; always shown next to its letter (G07, E24), never on its own. */
fun Constellation.color(): Color = when (this) {
    Constellation.GPS -> Palette.Gps
    Constellation.GLONASS -> Palette.Glonass
    Constellation.GALILEO -> Palette.Galileo
    Constellation.BEIDOU -> Palette.Beidou
    Constellation.QZSS -> Palette.Qzss
    Constellation.IRNSS -> Palette.Navic
    Constellation.SBAS, Constellation.UNKNOWN -> Palette.Augmentation
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
    MaterialTheme(colorScheme = InstrumentScheme, typography = PositioningInfoTypography, content = content)
}
