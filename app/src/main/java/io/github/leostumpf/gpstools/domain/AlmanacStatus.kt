// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

import io.github.leostumpf.gpstools.data.model.GnssSnapshot
import io.github.leostumpf.gpstools.data.model.SatelliteInfo
import io.github.leostumpf.gpstools.data.model.countSatellites

/**
 * How ready the receiver is to produce a fix, judged from the orbital data it holds.
 *
 * The two data sets behave very differently, and that difference is the whole point of
 * this screen:
 *
 * - the **almanac** is coarse orbital data for the entire constellation, broadcast by
 *   every satellite, valid for weeks. Without it the receiver has no idea which
 *   satellites are overhead and must search blindly.
 * - the **ephemeris** is precise orbital data for one satellite, valid a few hours.
 *   A fix cannot be computed without it.
 */
enum class AlmanacReadiness {
    /** Ephemeris for enough satellites: the next fix takes seconds. */
    HOT,

    /** Almanac but not enough ephemeris: the receiver knows where to look and must download the rest. */
    WARM,

    /** Neither: the receiver must search blindly, and downloading a full almanac takes minutes. */
    COLD,

    /** Nothing heard from the receiver yet. */
    UNKNOWN,
}

/** Per-constellation totals, for the breakdown table. */
data class ConstellationSummary(
    val constellation: Constellation,
    val visible: Int,
    val almanac: Int,
    val ephemeris: Int,
    val usedInFix: Int,
)

data class AlmanacStatus(
    val readiness: AlmanacReadiness,
    val visible: Int,
    val withAlmanac: Int,
    val withEphemeris: Int,
    val usedInFix: Int,
    val perConstellation: List<ConstellationSummary>,
    /**
     * True when the receiver is demonstrably computing fixes yet reports no ephemeris at
     * all. That combination is impossible, so the flag is not being populated on this
     * device and the count is withheld rather than shown as a misleading zero.
     *
     * Deliberately narrow: many phones report the almanac correctly while omitting the
     * ephemeris, so only the unreliable figure is suppressed.
     */
    val ephemerisUnavailable: Boolean,
) {

    companion object {

        /** A three-dimensional fix needs four satellites. */
        const val SATELLITES_FOR_FIX = 4

        fun from(snapshot: GnssSnapshot): AlmanacStatus {
            val sats = snapshot.satellites
            return AlmanacStatus(
                readiness = readinessOf(snapshot),
                ephemerisUnavailable = ephemerisUnavailable(snapshot),
                visible = snapshot.visibleCount,
                withAlmanac = snapshot.almanacCount,
                withEphemeris = snapshot.ephemerisCount,
                usedInFix = snapshot.usedInFixCount,
                perConstellation = summarise(sats),
            )
        }

        /**
         * A receiver cannot compute a fix without ephemeris, so being told it is doing so
         * with none on record means the flag is not implemented on this device.
         */
        private fun ephemerisUnavailable(snapshot: GnssSnapshot): Boolean =
            snapshot.hasReported &&
                snapshot.usedInFixCount >= SATELLITES_FOR_FIX &&
                snapshot.ephemerisCount == 0

        private fun readinessOf(snapshot: GnssSnapshot): AlmanacReadiness = when {
            !snapshot.hasReported -> AlmanacReadiness.UNKNOWN

            // A receiver actually using satellites for a fix is ready by demonstration,
            // whatever its flags claim. Observed behaviour outranks reported state.
            snapshot.usedInFixCount >= SATELLITES_FOR_FIX -> AlmanacReadiness.HOT

            snapshot.ephemerisCount >= SATELLITES_FOR_FIX -> AlmanacReadiness.HOT

            // Either kind of orbital data lifts the receiver out of a cold start. Any
            // ephemeris at all counts, because a receiver holding two or three precise
            // orbits is plainly not searching the sky blindly — it is simply short of the
            // four it needs.
            snapshot.almanacCount >= SATELLITES_FOR_FIX ||
                snapshot.ephemerisCount > 0 -> AlmanacReadiness.WARM

            else -> AlmanacReadiness.COLD
        }

        private fun summarise(sats: List<SatelliteInfo>): List<ConstellationSummary> =
            sats.groupBy { it.constellation }
                .map { (constellation, group) ->
                    ConstellationSummary(
                        constellation = constellation,
                        visible = group.countSatellites(),
                        almanac = group.countSatellites { it.hasAlmanac },
                        ephemeris = group.countSatellites { it.hasEphemeris },
                        usedInFix = group.countSatellites { it.usedInFix },
                    )
                }
                // Busiest constellation first, then by name so the order never jitters
                // between sweeps when two constellations are tied.
                .sortedWith(compareByDescending<ConstellationSummary> { it.visible }
                    .thenBy { it.constellation.label })
    }
}
