// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/**
 * Readings of one raw measurement that need translating before they mean anything:
 * the signal code, the carrier-phase state bits and the time uncertainty.
 */
object RawSignal {

    /** Speed of light in m/ns, to turn a time uncertainty into a range uncertainty. */
    private const val METRES_PER_NS = 0.299792458

    // GnssMeasurement.ADR_STATE_* bits, copied so this stays testable without Android.
    const val ADR_VALID = 1
    const val ADR_RESET = 2
    const val ADR_CYCLE_SLIP = 4
    const val ADR_HALF_CYCLE_RESOLVED = 8

    /**
     * Carrier-phase tracking of one signal in words. Null state means the chip reports
     * nothing; 0 means it does not track the phase of this signal.
     */
    fun carrierPhase(state: Int?): String = when {
        state == null || state == 0 -> "not tracked"
        state and ADR_RESET != 0 -> "reset"
        state and ADR_CYCLE_SLIP != 0 -> "cycle slip"
        state and ADR_VALID != 0 ->
            if (state and ADR_HALF_CYCLE_RESOLVED != 0) "valid, half-cycle resolved" else "valid"
        else -> "not valid"
    }

    /**
     * What a RINEX signal-code letter means for this constellation: which component of the
     * broadcast the chip tracks. Null for an unknown combination.
     */
    fun codeMeaning(constellation: Constellation, code: String): String? = when (code) {
        "C" -> when (constellation) {
            Constellation.GALILEO -> "E1-C pilot, no data"
            Constellation.BEIDOU, Constellation.IRNSS -> null
            else -> "C/A civil code"
        }
        "B" -> if (constellation == Constellation.GALILEO) "E1-B data channel" else null
        "X" -> "data and pilot combined"
        "I" -> "in-phase data channel"
        "Q" -> "quadrature pilot channel"
        "D" -> "data channel"
        "P" -> "pilot channel"
        "Z" -> "combined channels"
        "A" -> when (constellation) {
            Constellation.IRNSS -> "standard positioning service"
            Constellation.GALILEO -> "E1-A public regulated service"
            else -> null
        }
        "L" -> "long code"
        "S" -> "short code"
        else -> null
    }

    /** 1σ uncertainty of the received satellite time, as a range in metres. */
    fun timeUncertaintyM(uncertaintyNs: Long): Double = uncertaintyNs * METRES_PER_NS
}
