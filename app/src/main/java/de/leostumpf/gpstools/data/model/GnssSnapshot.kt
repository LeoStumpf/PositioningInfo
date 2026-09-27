// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.data.model

import de.leostumpf.gpstools.domain.Constellation

/**
 * A single location fix, reduced to the fields this app cares about and stripped of
 * Android types so the rest of the code stays testable.
 *
 * [elapsedRealtimeMs] comes from the monotonic elapsed-realtime clock rather than wall
 * time: fix age must stay correct across NTP corrections and timezone changes.
 */
data class SpeedFix(
    val speedMps: Float?,
    val speedAccuracyMps: Float?,
    val horizontalAccuracyM: Float?,
    val elapsedRealtimeMs: Long,
    /**
     * True for the cached fix used to seed the screen at startup, so something is shown
     * before the receiver reports. It is displayed like any other fix — its age still
     * governs whether it is trusted — but it is excluded from the session statistics,
     * which should only reflect what the app actually observed while open.
     */
    val isCached: Boolean = false,
)

/**
 * One satellite as the receiver currently sees it.
 *
 * [hasAlmanac] and [hasEphemeris] are the two kinds of orbital data a receiver needs.
 * The almanac is coarse, covers the whole constellation and stays valid for weeks; the
 * ephemeris is precise, covers one satellite and expires after a few hours. Together they
 * decide how long the next fix will take.
 */
data class SatelliteInfo(
    val svid: Int,
    val constellation: Constellation,
    /** Carrier-to-noise density in dB-Hz; roughly, signal strength. 0 when not tracked. */
    val cn0DbHz: Float,
    /** Degrees above the horizon. GnssStatus has no "unknown" flag for this; 0 means unlocated. */
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    /**
     * Carrier frequency in Hz, or null when the receiver does not report one. Identifies
     * which radio band the signal arrived on, and so whether the phone is tracking this
     * satellite on more than one frequency.
     */
    val carrierFrequencyHz: Float?,
)

/**
 * Everything the GNSS status callback reported in one sweep.
 *
 * The speedometer uses only the counts; the GNSS status screen uses the whole list. Both
 * read from this one snapshot so the two screens can never disagree.
 *
 * [satellites] is really a list of *signals*: a dual-frequency receiver reports a satellite
 * it hears on L1 and L5 as two entries with the same svid. The counts are therefore taken
 * over physical satellites, see [countSatellites].
 */
data class GnssSnapshot(
    val satellites: List<SatelliteInfo> = emptyList(),
    /** False until the receiver has actually reported, so "no data" is distinguishable from "nothing visible". */
    val hasReported: Boolean = false,
) {
    val visibleCount: Int get() = satellites.countSatellites()
    val usedInFixCount: Int get() = satellites.countSatellites { it.usedInFix }
    val almanacCount: Int get() = satellites.countSatellites { it.hasAlmanac }
    val ephemerisCount: Int get() = satellites.countSatellites { it.hasEphemeris }

    companion object {
        val EMPTY = GnssSnapshot()
    }
}

/**
 * Counts the physical satellites with at least one signal matching [predicate].
 *
 * A satellite heard on two bands appears in the list twice but is still one satellite, and
 * it is one satellite towards the four a fix needs. Counting signals instead would double
 * every figure on a dual-frequency phone and declare a fix possible from two satellites.
 */
fun List<SatelliteInfo>.countSatellites(predicate: (SatelliteInfo) -> Boolean = { true }): Int =
    filter(predicate).distinctBy { it.constellation to it.svid }.size
