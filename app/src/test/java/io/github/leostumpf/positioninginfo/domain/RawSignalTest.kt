// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawSignalTest {

    @Test
    fun `carrier phase states`() {
        assertEquals("not tracked", RawSignal.carrierPhase(null))
        assertEquals("not tracked", RawSignal.carrierPhase(0))
        assertEquals("valid", RawSignal.carrierPhase(RawSignal.ADR_VALID))
        assertEquals("valid, half-cycle resolved", RawSignal.carrierPhase(RawSignal.ADR_VALID or RawSignal.ADR_HALF_CYCLE_RESOLVED))
        // A reset or slip outweighs the valid bit: the accumulated range just broke.
        assertEquals("reset", RawSignal.carrierPhase(RawSignal.ADR_VALID or RawSignal.ADR_RESET))
        assertEquals("cycle slip", RawSignal.carrierPhase(RawSignal.ADR_VALID or RawSignal.ADR_CYCLE_SLIP))
        assertEquals("not valid", RawSignal.carrierPhase(16))
    }

    @Test
    fun `code letters depend on the constellation`() {
        assertEquals("C/A civil code", RawSignal.codeMeaning(Constellation.GPS, "C"))
        assertEquals("E1-C pilot, no data", RawSignal.codeMeaning(Constellation.GALILEO, "C"))
        assertEquals("E1-B data channel", RawSignal.codeMeaning(Constellation.GALILEO, "B"))
        assertNull(RawSignal.codeMeaning(Constellation.GPS, "B"))
        assertNull(RawSignal.codeMeaning(Constellation.GPS, "?"))
    }

    @Test
    fun `time uncertainty converts to metres at the speed of light`() {
        assertEquals(29.98, RawSignal.timeUncertaintyM(100), 0.01)
    }

    @Test
    fun `clock offsets in words`() {
        assertEquals("in sync (within 1 s)", describeOffset(-400, 1_000, "GNSS time"))
        assertEquals("in sync (within 100 ms)", describeOffset(40, 100, "network time"))
        assertEquals("0.25 s ahead of network time", describeOffset(250, 100, "network time"))
        assertEquals("3.10 s behind GNSS time", describeOffset(-3_100, 1_000, "GNSS time"))
        assertEquals("125 s ahead of GNSS time", describeOffset(125_400, 1_000, "GNSS time"))
    }
}
