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
    E5B("E5b / B2b / L3", "Galileo and BeiDou secondary band, and GLONASS L3"),
    E6("E6 / B3 / L6", "Galileo E6, BeiDou B3I and QZSS L6: precise-positioning services"),
    S_BAND("S", "NavIC S-band"),
    UNKNOWN("—", "Unrecognised carrier frequency"),
    ;

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
            val mhz = hz / HZ_PER_MHZ
            return RANGES_MHZ.firstOrNull { (range, _) -> mhz in range }?.second ?: UNKNOWN
        }

        private const val HZ_PER_MHZ = 1_000_000.0

        /** Each band's span in MHz, with the signals that fall into it. */
        private val RANGES_MHZ = listOf(
            // GPS L1, Galileo E1, BeiDou B1, and the GLONASS L1 FDMA channels up to 1610 MHz
            1_555.0..<1_610.0 to L1,
            // GPS L2C, GLONASS L2
            1_215.0..<1_255.0 to L2,
            // GPS L5, Galileo E5a, BeiDou B2a (and Galileo's E5 AltBOC centre at 1191.795)
            1_164.0..<1_192.0 to L5,
            // Galileo E5b, BeiDou B2b, GLONASS L3
            1_192.0..<1_215.0 to E5B,
            // BeiDou B3I 1268.52, Galileo E6 and QZSS L6 1278.75
            1_255.0..<1_300.0 to E6,
            // NavIC S
            2_480.0..<2_500.0 to S_BAND,
        )
    }
}
