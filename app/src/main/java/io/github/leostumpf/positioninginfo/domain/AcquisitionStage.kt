// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/**
 * How far the receiver has got with one satellite's signal.
 *
 * A satellite is not usable the moment it is heard: the receiver must lock onto its code,
 * find the bit edges, find the frame, and decode the time it was sent. Only then can the
 * range be measured. Showing the stage explains why a "visible" satellite is not yet in
 * the fix. Decoded from GnssMeasurement.state; constants are Android's, kept as numbers so
 * this stays free of Android imports.
 */
enum class AcquisitionStage(val step: Int, val label: String, val meaning: String) {
    SEARCHING(0, "Searching", "Energy on the frequency, but no lock on the satellite's code yet."),
    CODE_LOCK(1, "Code lock", "Locked onto the satellite's ranging code; the time within one millisecond is known."),
    BIT_SYNC(2, "Bit sync", "The edges of the data bits are found; the navigation message can be read."),
    FRAME_SYNC(3, "Frame sync", "The start of a message frame is found; the data can be decoded."),
    TIME_DECODED(4, "Time decoded", "The transmit time is known: the range can be measured and the satellite used."),
    ;

    companion object {
        const val STEPS = 4

        private const val CODE_LOCK_BITS = 1 or 1024 or 2048 or 65536          // CODE_LOCK, GAL_E1BC_CODE_LOCK, GAL_E1C_2ND_CODE_LOCK, 2ND_CODE_LOCK
        private const val BIT_SYNC_BITS = 2 or 32 or 256                        // BIT_SYNC, SYMBOL_SYNC, BDS_D2_BIT_SYNC
        private const val FRAME_SYNC_BITS = 4 or 64 or 512 or 4096 or 8192      // SUBFRAME_SYNC, GLO_STRING_SYNC, BDS_D2_SUBFRAME_SYNC, GAL_E1B_PAGE_SYNC, SBAS_SYNC
        private const val TIME_BITS = 8 or 128 or 16384 or 32768                // TOW_DECODED, GLO_TOD_DECODED, TOW_KNOWN, GLO_TOD_KNOWN

        /** The highest stage the state flags show. */
        fun from(state: Int): AcquisitionStage = when {
            state and TIME_BITS != 0 -> TIME_DECODED
            state and FRAME_SYNC_BITS != 0 -> FRAME_SYNC
            state and BIT_SYNC_BITS != 0 -> BIT_SYNC
            state and CODE_LOCK_BITS != 0 -> CODE_LOCK
            else -> SEARCHING
        }

        private const val SPEED_OF_LIGHT = 299_792_458.0

        /** Doppler shift from the pseudorange rate: approaching satellites raise the frequency. */
        fun dopplerHz(pseudorangeRateMps: Double, carrierHz: Double): Double = -pseudorangeRateMps * carrierHz / SPEED_OF_LIGHT
    }
}
