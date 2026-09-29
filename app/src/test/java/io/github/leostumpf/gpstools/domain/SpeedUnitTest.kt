// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedUnitTest {

    @Test
    fun `converts metres per second to each unit`() {
        val mps = 10.0
        assertEquals(36.0, SpeedUnit.KMH.fromMps(mps), 0.001)
        assertEquals(22.369, SpeedUnit.MPH.fromMps(mps), 0.001)
        assertEquals(19.438, SpeedUnit.KNOTS.fromMps(mps), 0.001)
    }

    @Test
    fun `one knot is 1_852 kilometres per hour`() {
        // The NMEA test feed emits speed in knots, so this relationship is what the
        // on-device verification asserts against.
        val oneKnotInMps = 1.0 / SpeedUnit.KNOTS.fromMps(1.0)
        assertEquals(1.852, SpeedUnit.KMH.fromMps(oneKnotInMps), 0.001)
    }

    @Test
    fun `zero stays zero in every unit`() {
        SpeedUnit.entries.forEach { assertEquals(0.0, it.fromMps(0.0), 0.0) }
    }

    @Test
    fun `next cycles through all units and wraps`() {
        assertEquals(SpeedUnit.MPH, SpeedUnit.KMH.next())
        assertEquals(SpeedUnit.KNOTS, SpeedUnit.MPH.next())
        assertEquals(SpeedUnit.KMH, SpeedUnit.KNOTS.next())
    }

    @Test
    fun `unknown persisted name falls back to the default`() {
        assertEquals(SpeedUnit.DEFAULT, SpeedUnit.fromName("FURLONGS_PER_FORTNIGHT"))
        assertEquals(SpeedUnit.DEFAULT, SpeedUnit.fromName(null))
        assertEquals(SpeedUnit.KNOTS, SpeedUnit.fromName("KNOTS"))
    }
}
