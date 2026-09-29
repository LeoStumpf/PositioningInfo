// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DopTest {

    @Test
    fun `fewer than four satellites give no dop`() {
        assertNull(DopCalculator.of(emptyList()))
        assertNull(DopCalculator.of(List(3) { SkyPoint(it * 120f, 45f) }))
    }

    @Test
    fun `four satellites in the same direction are singular`() {
        assertNull(DopCalculator.of(List(4) { SkyPoint(90f, 30f) }))
    }

    @Test
    fun `zenith plus three on the horizon matches the analytic result`() {
        // Rows: zenith [0, 0, 1, 1]; horizon [sin az, cos az, 0, 1] for az = 0°, 120°, 240°.
        // HᵀH: EE = Σsin² = 3/2, NN = Σcos² = 3/2, EN = ET = NT = EU = NU = 0,
        //      UU = 1, UT = 1, TT = 4.
        // So qEE = qNN = 2/3, and the U-T block [[1, 1], [1, 4]] inverts to
        // [[4/3, -1/3], [-1/3, 1/3]]: qUU = 4/3, qTT = 1/3.
        // HDOP = √(4/3), VDOP = √(4/3), PDOP = √(8/3), TDOP = √(1/3).
        val dop = DopCalculator.of(
            listOf(SkyPoint(0f, 90f), SkyPoint(0f, 0f), SkyPoint(120f, 0f), SkyPoint(240f, 0f)),
        )
        assertNotNull(dop)
        assertEquals(sqrt(4.0 / 3), dop!!.hdop, 1e-6)
        assertEquals(sqrt(4.0 / 3), dop.vdop, 1e-6)
        assertEquals(sqrt(8.0 / 3), dop.pdop, 1e-6)
        assertEquals(sqrt(1.0 / 3), dop.tdop, 1e-6)
        assertEquals(DopRating.EXCELLENT, dop.rating)
    }

    @Test
    fun `an even spread beats a bunched one`() {
        val spread = listOf(SkyPoint(0f, 85f)) + List(7) { SkyPoint(it * 360f / 7, 20f) }
        val bunched = List(8) { SkyPoint(80f + it * 5f, 30f + (it % 3) * 10f) }
        val good = DopCalculator.of(spread)!!
        val poor = DopCalculator.of(bunched)!!
        assertTrue(good.pdop < poor.pdop)
        assertTrue(good.pdop < 2.0)
    }

    @Test
    fun `hdop and vdop add up to pdop in quadrature`() {
        val dop = DopCalculator.of(
            listOf(SkyPoint(10f, 70f), SkyPoint(100f, 35f), SkyPoint(200f, 15f), SkyPoint(290f, 50f), SkyPoint(45f, 25f)),
        )!!
        assertEquals(dop.pdop * dop.pdop, dop.hdop * dop.hdop + dop.vdop * dop.vdop, 1e-9)
    }

    @Test
    fun `rating follows the customary thresholds`() {
        assertEquals(DopRating.IDEAL, DopRating.of(1.0))
        assertEquals(DopRating.EXCELLENT, DopRating.of(2.0))
        assertEquals(DopRating.GOOD, DopRating.of(5.0))
        assertEquals(DopRating.MODERATE, DopRating.of(10.0))
        assertEquals(DopRating.FAIR, DopRating.of(20.0))
        assertEquals(DopRating.POOR, DopRating.of(20.1))
    }
}
