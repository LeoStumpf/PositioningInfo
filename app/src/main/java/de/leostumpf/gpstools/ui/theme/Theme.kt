// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.leostumpf.gpstools.domain.Constellation

/**
 * "Instrument Black": the app's design tokens.
 *
 * The chrome is monochrome — white and greys on true black — so that colour is only ever
 * information: a state ([Good], [Degraded], [Bad]) or a constellation. True black because
 * the app is read at night in car mounts, where an OLED black costs no light and no
 * attention; not tied to the system theme for the same reason an instrument cluster isn't.
 */
object Palette {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0D0D0D)
    val SurfaceRaised = Color(0xFF161616)
    val Sheet = Color(0xFF121212)
    val CardBorder = Color(0xFF1F1F1F)
    val Divider = Color(0xFF1A1A1A)
    val Hairline = Color(0xFF242424)
    val Outline = Color(0xFF333333)

    val TextPrimary = Color(0xFFEDEDED)
    val TextSecondary = Color(0xFFA6A6A6)
    /** 4.9:1 on black: the dimmest text that still passes WCAG AA. */
    val TextTertiary = Color(0xFF7A7A7A)
    val Inactive = Color(0xFF5A5A5A)

    val Good = Color(0xFF5ED39A)
    val Degraded = Color(0xFFF3B64A)
    val Bad = Color(0xFFFF7A6B)
}

// Names the screens already use, now pointing at the tokens.
val OkGreen = Palette.Good
val WarnAmber = Palette.Degraded
val ErrorRed = Palette.Bad
val DimGrey = Palette.TextTertiary

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
fun GpsToolsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InstrumentScheme,
        typography = GpsToolsTypography,
        content = content,
    )
}
