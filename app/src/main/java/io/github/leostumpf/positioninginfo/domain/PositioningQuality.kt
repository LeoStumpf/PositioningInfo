// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import io.github.leostumpf.positioninginfo.data.model.countSatellites

/**
 * The resolution the receiver should be capable of, given what it is currently using.
 *
 * These are typical open-sky figures for the technique in play, not a measurement and not
 * a promise: the actual error on any given fix depends on satellite geometry, buildings,
 * trees and the weather in the ionosphere. The measured accuracy the receiver reports for
 * the current fix is the real number, and the screen shows that alongside.
 */
enum class ResolutionClass(val label: String, val typicalRange: String) {
    DUAL_FREQUENCY_AUGMENTED("Dual frequency + SBAS", "1–2 m"),
    DUAL_FREQUENCY("Dual frequency", "2–3 m"),
    AUGMENTED("Single frequency + SBAS", "1–3 m"),
    MULTI_CONSTELLATION("Multi-constellation", "3–5 m"),
    SINGLE_CONSTELLATION("Single constellation", "5–10 m"),
    NO_FIX("No fix", "—"),
}

/**
 * Which techniques the current fix is actually built on.
 *
 * Deliberately derived from the satellites **used in the fix** rather than those merely
 * visible: a constellation the receiver can see but is not using contributes nothing to
 * the accuracy of the position it is reporting.
 */
data class PositioningQuality(
    val resolution: ResolutionClass,
    val bandsInUse: List<SignalBand>,
    val constellationsInUse: List<Constellation>,
    val sbasInView: List<SbasSystem>,
    val sbasUsedInFix: Boolean,
    val dualFrequency: Boolean,
    /** True when the receiver reports no carrier frequencies at all, so bands cannot be shown. */
    val bandsUnavailable: Boolean,
) {
    companion object {

        fun from(snapshot: GnssSnapshot): PositioningQuality {
            val used = snapshot.satellites.filter { it.usedInFix }

            val bands = used.mapNotNull { it.band }.distinct().sortedBy { it.ordinal }
            val constellations = used.map { it.constellation }
                .filter { it != Constellation.SBAS }
                .distinct()
                .sortedBy { it.ordinal }

            // SBAS satellites are worth listing even when unused: seeing EGNOS overhead but
            // not in the fix is itself the answer to "why am I not getting corrections?".
            val sbasInView = snapshot.satellites
                .filter { it.constellation == Constellation.SBAS }
                .map { SbasSystem.fromSvid(it.svid) }
                .distinct()
                .sortedBy { it.ordinal }

            val sbasUsed = used.any { it.constellation == Constellation.SBAS }
            val dualFrequency = bands.any { it.isHighPrecision } && bands.contains(SignalBand.L1)

            return PositioningQuality(
                resolution = classify(
                    hasFix = used.countSatellites() >= AlmanacStatus.SATELLITES_FOR_FIX,
                    dualFrequency = dualFrequency,
                    sbasUsed = sbasUsed,
                    constellationCount = constellations.size,
                ),
                bandsInUse = bands,
                constellationsInUse = constellations,
                sbasInView = sbasInView,
                sbasUsedInFix = sbasUsed,
                dualFrequency = dualFrequency,
                bandsUnavailable = used.isNotEmpty() && used.all { it.band == null },
            )
        }

        private fun classify(
            hasFix: Boolean,
            dualFrequency: Boolean,
            sbasUsed: Boolean,
            constellationCount: Int,
        ): ResolutionClass = when {
            !hasFix -> ResolutionClass.NO_FIX
            dualFrequency && sbasUsed -> ResolutionClass.DUAL_FREQUENCY_AUGMENTED
            dualFrequency -> ResolutionClass.DUAL_FREQUENCY
            sbasUsed -> ResolutionClass.AUGMENTED
            constellationCount > 1 -> ResolutionClass.MULTI_CONSTELLATION
            else -> ResolutionClass.SINGLE_CONSTELLATION
        }
    }
}

/** The band this satellite's carrier frequency falls in, or null when none was reported. */
val SatelliteInfo.band: SignalBand?
    get() = carrierFrequencyHz?.let { SignalBand.fromCarrierFrequencyHz(it) }
