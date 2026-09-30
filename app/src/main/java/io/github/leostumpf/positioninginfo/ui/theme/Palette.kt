// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.theme

import androidx.compose.ui.graphics.Color

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

    /** Between rows of a dense list, fainter than [Divider]. */
    val RowDivider = Color(0xFF151515)

    /** The sky plot's disc, a shade off black. */
    val PlotBackground = Color(0xFF060606)

    /** The dimmest step of a greyscale ramp, below [Inactive]. */
    val Faint = Color(0xFF444444)

    val TextPrimary = Color(0xFFEDEDED)
    val TextSecondary = Color(0xFFA6A6A6)

    /** 4.9:1 on black: the dimmest text that still passes WCAG AA. */
    val TextTertiary = Color(0xFF7A7A7A)

    /** Switched-off text; #767676 still clears 4.5:1 against black. */
    val Inactive = Color(0xFF767676)

    val Good = Color(0xFF5ED39A)
    val Degraded = Color(0xFFF3B64A)
    val Bad = Color(0xFFFF7A6B)

    // One colour per constellation, each next to its letter (G07, E24), never alone.
    val Gps = Color(0xFF6FB7FF)
    val Glonass = Color(0xFFFF8F80)
    val Galileo = Color(0xFFF2C94C)
    val Beidou = Color(0xFFB8A2FF)
    val Qzss = Color(0xFF5CD6C8)
    val Navic = Color(0xFFF595C8)
    val Augmentation = Color(0xFFA0A0A0)
}
