// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalBandTest {

    private fun mhz(v: Double) = SignalBand.fromCarrierFrequencyHz((v * 1_000_000).toFloat())

    @Test
    fun `identifies the L1 family across constellations`() {
        assertEquals(SignalBand.L1, mhz(1575.42))   // GPS L1 C/A, Galileo E1, SBAS L1
        assertEquals(SignalBand.L1, mhz(1561.098))  // BeiDou B1I
        assertEquals(SignalBand.L1, mhz(1602.0))    // GLONASS L1, centre of the FDMA spread
        assertEquals(SignalBand.L1, mhz(1598.0625)) // GLONASS L1, lowest channel
        assertEquals(SignalBand.L1, mhz(1605.375))  // GLONASS L1, highest channel
    }

    @Test
    fun `identifies the high-precision L5 family`() {
        assertEquals(SignalBand.L5, mhz(1176.45))   // GPS L5, Galileo E5a, BeiDou B2a
        assertEquals(SignalBand.L5, mhz(1191.795))  // Galileo E5 AltBOC centre
    }

    @Test
    fun `separates E5b from L5`() {
        assertEquals(SignalBand.E5B, mhz(1207.14))  // Galileo E5b, BeiDou B2I
    }

    @Test
    fun `identifies the L2 band`() {
        assertEquals(SignalBand.L2, mhz(1227.60))   // GPS L2C
        assertEquals(SignalBand.L2, mhz(1246.0))    // GLONASS L2
    }

    @Test
    fun `identifies the NavIC S band`() {
        assertEquals(SignalBand.S_BAND, mhz(2492.028))
    }

    @Test
    fun `frequencies outside the navigation bands are not guessed at`() {
        assertEquals(SignalBand.UNKNOWN, mhz(0.0))
        assertEquals(SignalBand.UNKNOWN, mhz(900.0))
        assertEquals(SignalBand.UNKNOWN, mhz(1800.0))
    }

    @Test
    fun `only the modern bands count as high precision`() {
        assertEquals(
            listOf(SignalBand.L5, SignalBand.E5B),
            SignalBand.entries.filter { it.isHighPrecision },
        )
    }
}
