// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/**
 * The radio band a satellite signal arrives on, identified from its carrier frequency.
 *
 * This matters more than any other single figure on the signal screen. A receiver that can
 * hear a satellite on two widely separated frequencies can measure the ionospheric delay
 * directly and cancel it, instead of relying on a broadcast model. That is the difference
 * between metre-level and sub-metre-level positioning, and it is why "dual frequency"
 * appears on phone spec sheets.
 */
enum class SignalBand(val label: String, val description: String) {
    L1("L1 / E1 / B1", "Legacy civil band, carried by every constellation"),
    L2("L2", "Second civil band, mostly GPS and GLONASS"),
    L5("L5 / E5a / B2a", "Modern high-precision band, resists multipath and ionospheric error"),
    E5B("E5b / B2b", "Galileo and BeiDou secondary band"),
    S_BAND("S", "NavIC S-band"),
    UNKNOWN("—", "Unrecognised carrier frequency");

    val isHighPrecision: Boolean get() = this == L5 || this == E5B

    companion object {
        /**
         * Classifies a carrier frequency in Hz.
         *
         * Ranges rather than exact values, because GLONASS uses frequency-division access
         * — each satellite transmits on its own slightly different carrier — and every
         * constellation places its signals a little differently within a band.
         */
        fun fromCarrierFrequencyHz(hz: Float): SignalBand {
            val mhz = hz / 1_000_000.0
            return when {
                mhz >= 1_555.0 && mhz < 1_595.0 -> L1        // GPS L1, Galileo E1, BeiDou B1
                mhz >= 1_595.0 && mhz < 1_610.0 -> L1        // GLONASS L1 (FDMA spread)
                mhz >= 1_215.0 && mhz < 1_255.0 -> L2        // GPS L2C, GLONASS L2
                mhz >= 1_164.0 && mhz < 1_192.0 -> L5        // GPS L5, Galileo E5a, BeiDou B2a
                mhz >= 1_192.0 && mhz < 1_215.0 -> E5B       // Galileo E5b, BeiDou B2b
                mhz >= 2_480.0 && mhz < 2_500.0 -> S_BAND    // NavIC S
                else -> UNKNOWN
            }
        }
    }
}
