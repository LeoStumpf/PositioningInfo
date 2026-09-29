// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

/**
 * The satellite navigation systems Android can report.
 *
 * Mirrors the `GnssStatus.CONSTELLATION_*` constants, kept as a domain type so everything
 * above the data layer stays free of Android imports and unit-testable.
 */
enum class Constellation(val label: String, val operator: String) {
    GPS("GPS", "United States"),
    GLONASS("GLONASS", "Russia"),
    GALILEO("Galileo", "European Union"),
    BEIDOU("BeiDou", "China"),
    QZSS("QZSS", "Japan"),
    IRNSS("NavIC", "India"),
    SBAS("SBAS", "Augmentation"),
    UNKNOWN("Unknown", "");

    companion object {
        /** Constellation types as defined by `android.location.GnssStatus`. */
        fun fromAndroidType(type: Int): Constellation = when (type) {
            1 -> GPS
            2 -> SBAS
            3 -> GLONASS
            4 -> QZSS
            5 -> BEIDOU
            6 -> GALILEO
            7 -> IRNSS
            else -> UNKNOWN
        }
    }
}
