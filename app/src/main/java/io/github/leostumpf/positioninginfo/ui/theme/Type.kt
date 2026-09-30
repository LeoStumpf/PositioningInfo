// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The fonts every Android phone already has, so nothing is bundled or downloaded:
 * sans-serif (Roboto) for text, its condensed cut for the big readings, and the system
 * monospace for data. Tabular figures (below) keep numbers from jumping.
 */
val SansFamily = FontFamily.SansSerif
val CondensedFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed-light"), FontWeight.Light),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
)
val MonoFamily = FontFamily.Monospace

/**
 * Tabular figures. Without this a live number visibly jitters sideways as digits change
 * width, which is the single most distracting thing a large updating reading can do.
 */
private const val TABULAR = "tnum"

/** The speed itself. */
val ReadoutStyle = TextStyle(
    fontFamily = CondensedFamily,
    fontWeight = FontWeight.Light,
    fontSize = 168.sp,
    lineHeight = 160.sp,
    letterSpacing = (-0.04).em,
    fontFeatureSettings = TABULAR,
)

/** A page's main measurement: accuracy, altitude, distance. */
val HeroStyle = TextStyle(
    fontFamily = CondensedFamily,
    fontWeight = FontWeight.Light,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    fontFeatureSettings = TABULAR,
)

/** Values in stat tiles. */
val TileValueStyle = TextStyle(
    fontFamily = CondensedFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    fontFeatureSettings = TABULAR,
)

val PageTitleStyle = TextStyle(
    fontFamily = SansFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.01).em,
)

val TitleStyle = TextStyle(
    fontFamily = SansFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp,
)

val BodyStyle = TextStyle(fontFamily = SansFamily, fontSize = 15.sp, lineHeight = 22.sp)

val CaptionStyle = TextStyle(fontFamily = SansFamily, fontSize = 13.sp, lineHeight = 19.sp)

/** Numbers and codes in rows. */
val DataStyle = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR)

/** Section labels and small caps markers. */
val OverlineStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.08.em,
)

/** Small monospaced detail, e.g. the speed page's status line. */
val StatusLineStyle = TextStyle(
    fontFamily = MonoFamily,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontFeatureSettings = TABULAR,
)

/** Material's roles in the same families, so dialogs and sheets match the pages. */
val PositioningInfoTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = CondensedFamily),
        displayMedium = displayMedium.copy(fontFamily = CondensedFamily),
        displaySmall = displaySmall.copy(fontFamily = CondensedFamily),
        headlineLarge = headlineLarge.copy(fontFamily = SansFamily),
        headlineMedium = headlineMedium.copy(fontFamily = SansFamily),
        headlineSmall = headlineSmall.copy(fontFamily = SansFamily, fontWeight = FontWeight.Medium),
        titleLarge = titleLarge.copy(fontFamily = SansFamily, fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontFamily = SansFamily, fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.copy(fontFamily = SansFamily),
        bodyLarge = bodyLarge.copy(fontFamily = SansFamily),
        bodyMedium = BodyStyle,
        bodySmall = CaptionStyle,
        labelLarge = labelLarge.copy(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp),
        labelMedium = labelMedium.copy(fontFamily = SansFamily),
        labelSmall = labelSmall.copy(fontFamily = MonoFamily),
    )
}
