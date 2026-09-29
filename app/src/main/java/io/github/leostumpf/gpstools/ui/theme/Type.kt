// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.leostumpf.gpstools.R

/**
 * IBM Plex, bundled (SIL OFL 1.1, licence under assets/licenses): Sans for text, Sans
 * Condensed for the big readings, Mono for data. Nothing is downloaded — the app has no
 * network access.
 */
val PlexSans = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
)
val PlexCondensed = FontFamily(
    Font(R.font.plex_condensed_light, FontWeight.Light),
    Font(R.font.plex_condensed_regular, FontWeight.Normal),
)
val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
)

/**
 * Tabular figures. Without this a live number visibly jitters sideways as digits change
 * width, which is the single most distracting thing a large updating reading can do.
 */
private const val TABULAR = "tnum"

/** The speed itself. */
val ReadoutStyle = TextStyle(
    fontFamily = PlexCondensed, fontWeight = FontWeight.Light, fontSize = 168.sp,
    lineHeight = 160.sp, letterSpacing = (-0.04).em, fontFeatureSettings = TABULAR,
)

/** A page's main measurement: accuracy, altitude, distance. */
val HeroStyle = TextStyle(
    fontFamily = PlexCondensed, fontWeight = FontWeight.Light, fontSize = 56.sp,
    lineHeight = 60.sp, fontFeatureSettings = TABULAR,
)

/** Values in stat tiles. */
val TileValueStyle = TextStyle(
    fontFamily = PlexCondensed, fontWeight = FontWeight.Normal, fontSize = 26.sp,
    lineHeight = 30.sp, fontFeatureSettings = TABULAR,
)

val PageTitleStyle = TextStyle(
    fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 24.sp,
    lineHeight = 30.sp, letterSpacing = (-0.01).em,
)

val TitleStyle = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp)

val BodyStyle = TextStyle(fontFamily = PlexSans, fontSize = 15.sp, lineHeight = 22.sp)

val CaptionStyle = TextStyle(fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 19.sp)

/** Numbers and codes in rows. */
val DataStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR)

/** Section labels and small caps markers. */
val OverlineStyle = TextStyle(
    fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.sp,
    lineHeight = 14.sp, letterSpacing = 0.08.em,
)

/** Small monospaced detail, e.g. the speed page's status line. */
val StatusLineStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR)

/** Material's roles, all in Plex so dialogs and sheets match the pages. */
val GpsToolsTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = PlexCondensed),
        displayMedium = displayMedium.copy(fontFamily = PlexCondensed),
        displaySmall = displaySmall.copy(fontFamily = PlexCondensed),
        headlineLarge = headlineLarge.copy(fontFamily = PlexSans),
        headlineMedium = headlineMedium.copy(fontFamily = PlexSans),
        headlineSmall = headlineSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
        titleLarge = titleLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.copy(fontFamily = PlexSans),
        bodyLarge = bodyLarge.copy(fontFamily = PlexSans),
        bodyMedium = BodyStyle,
        bodySmall = CaptionStyle,
        labelLarge = labelLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
        labelMedium = labelMedium.copy(fontFamily = PlexSans),
        labelSmall = labelSmall.copy(fontFamily = PlexMono),
    )
}
