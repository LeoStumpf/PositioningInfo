// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.domain

/**
 * Satellite-based augmentation systems: the geostationary satellites that broadcast
 * correction data for the constellations below them.
 *
 * These are the auxiliary service a phone receives over the air, as opposed to assistance
 * delivered over the network. Which one is overhead depends on where you are, so
 * identifying it by PRN tells you which regional service is actually in range.
 */
enum class SbasSystem(val label: String, val region: String) {
    WAAS("WAAS", "United States"),
    EGNOS("EGNOS", "European Union"),
    MSAS("MSAS", "Japan"),
    GAGAN("GAGAN", "India"),
    SDCM("SDCM", "Russia"),
    BDSBAS("BDSBAS", "China"),
    KASS("KASS", "South Korea"),
    SOUTHPAN("SouthPAN", "Australia / New Zealand"),
    UNKNOWN_SBAS("SBAS", "Unidentified");

    companion object {
        /**
         * Identifies an augmentation satellite from its PRN.
         *
         * Android reports SBAS satellites with the PRN as the svid, in the 120-158 range
         * these systems are allocated. The allocations are published by ICAO and are
         * stable, but individual satellites do get reassigned between services, so an
         * unrecognised PRN falls back to the generic label rather than guessing at it.
         */
        fun fromSvid(svid: Int): SbasSystem = when (svid) {
            120, 121, 123, 124, 126, 136 -> EGNOS
            122 -> SOUTHPAN
            125, 140, 141 -> SDCM
            127, 128, 132 -> GAGAN
            129, 137 -> MSAS
            130, 143, 144 -> BDSBAS
            131, 133, 135, 138 -> WAAS
            134 -> KASS
            else -> UNKNOWN_SBAS
        }
    }
}
