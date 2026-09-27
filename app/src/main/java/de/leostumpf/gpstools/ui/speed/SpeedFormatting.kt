// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.speed

import de.leostumpf.gpstools.domain.SpeedUnit
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
    return if (value >= 100.0) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

fun formatSpeed(mps: Float?, unit: SpeedUnit): String = formatSpeed(mps?.toDouble(), unit)

fun formatAccuracy(meters: Float?): String =
    if (meters == null) NO_VALUE else String.format(Locale.US, "±%.1f m", meters)
