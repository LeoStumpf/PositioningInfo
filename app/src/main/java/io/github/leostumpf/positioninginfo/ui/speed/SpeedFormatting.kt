// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.speed

import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import java.util.Locale

/** Placeholder shown whenever there is no trustworthy reading. */
const val NO_VALUE = "--"

/**
 * Formats a speed the way a speedometer should: one decimal at low speeds where it carries
 * real information, whole numbers above 100 where the extra digit is only noise and costs
 * glanceable width.
 */
fun formatSpeed(mps: Double?, unit: SpeedUnit): String {
    if (mps == null) return NO_VALUE
    val value = unit.fromMps(mps)
    return if (value >= WHOLE_NUMBERS_FROM) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

/** From 100 up the decimal is noise and costs a digit's width. */
private const val WHOLE_NUMBERS_FROM = 100.0

/** As the [Double] version: [NO_VALUE] when null, whole numbers from 100. */
fun formatSpeed(mps: Float?, unit: SpeedUnit): String = formatSpeed(mps?.toDouble(), unit)

/** "±4.2 m", or [NO_VALUE] when the fix reports no accuracy. */
fun formatAccuracy(meters: Float?): String =
    if (meters == null) NO_VALUE else String.format(Locale.US, "±%.1f m", meters)
