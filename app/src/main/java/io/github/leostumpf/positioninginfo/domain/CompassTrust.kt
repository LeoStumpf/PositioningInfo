// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import kotlin.math.abs

/**
 * Whether the compass can be believed right now.
 *
 * The Earth's field has a known strength at every place (about 48 µT in central Europe),
 * which Android's built-in World Magnetic Model provides. A magnetometer measuring much
 * more or less is being disturbed — a magnet in a car mount, a laptop, steel nearby — and
 * its heading is then wrong by an unknown amount.
 */
data class CompassTrust(val measuredUt: Double, val expectedUt: Double) {
    /** Relative difference, 0.12 for 12 %; infinite when no field is expected at all. */
    val deviation: Double
        get() = if (expectedUt > 0.0) abs(measuredUt - expectedUt) / expectedUt else Double.POSITIVE_INFINITY

    val level: Level
        get() = when {
            deviation <= RELIABLE -> Level.RELIABLE
            deviation <= SUSPECT -> Level.SUSPECT
            else -> Level.DISTURBED
        }

    enum class Level { RELIABLE, SUSPECT, DISTURBED }

    companion object {
        const val RELIABLE = 0.10
        const val SUSPECT = 0.25

        /** Field strength from the three axes, in µT. */
        fun magnitude(x: Float, y: Float, z: Float): Double = kotlin.math.sqrt((x * x + y * y + z * z).toDouble())
    }
}
