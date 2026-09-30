// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

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
    UNKNOWN("Unknown", ""),
    ;

    companion object {
        /** Constellation types as defined by `android.location.GnssStatus.CONSTELLATION_*`. */
        fun fromAndroidType(type: Int): Constellation = ANDROID_TYPES[type] ?: UNKNOWN

        /** `GnssStatus.CONSTELLATION_GPS` … `CONSTELLATION_IRNSS`, which are 1 to 7. */
        private val ANDROID_TYPES = mapOf(
            1 to GPS,
            2 to SBAS,
            3 to GLONASS,
            4 to QZSS,
            5 to BEIDOU,
            6 to GALILEO,
            7 to IRNSS,
        )
    }
}
