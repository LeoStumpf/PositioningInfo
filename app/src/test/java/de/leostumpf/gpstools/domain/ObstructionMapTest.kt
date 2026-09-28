// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.domain

import de.leostumpf.gpstools.data.model.SatelliteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObstructionMapTest {

    private fun sat(
        svid: Int,
        az: Float,
        el: Float,
        cn0: Float = 30f,
        freq: Float? = null,
    ) = SatelliteInfo(
        svid = svid,
        constellation = Constellation.GPS,
        cn0DbHz = cn0,
        elevationDegrees = el,
        azimuthDegrees = az,
        usedInFix = true,
        hasAlmanac = true,
        hasEphemeris = true,
        carrierFrequencyHz = freq,
    )

    @Test
    fun `azimuth just below 360 lands in the last bin and 0 in the first`() {
        val cells = ObstructionMap().onSnapshot(listOf(sat(1, 359.9f, 45f), sat(2, 0f, 45f))).cells
        assertEquals(2, cells.size)
        assertEquals(0f, cells[0].azimuthFrom)
        assertEquals(10f, cells[0].azimuthTo)
        assertEquals(350f, cells[1].azimuthFrom)
        assertEquals(360f, cells[1].azimuthTo)
        assertTrue(cells.all { it.elevationFrom == 40f && it.elevationTo == 50f })
    }

    @Test
    fun `the zenith goes in the top elevation bin`() {
        val cell = ObstructionMap().onSnapshot(listOf(sat(1, 120f, 90f))).cells.single()
        assertEquals(80f, cell.elevationFrom)
        assertEquals(90f, cell.elevationTo)
        assertEquals(120f, cell.azimuthFrom)
    }

    @Test
    fun `mean and max accumulate across snapshots`() {
        val map = ObstructionMap()
            .onSnapshot(listOf(sat(1, 12f, 33f, cn0 = 20f)))
            .onSnapshot(listOf(sat(1, 13f, 34f, cn0 = 40f)))
            .onSnapshot(listOf(sat(1, 14f, 35f, cn0 = 30f)))
        val cell = map.cells.single()
        assertEquals(30f, cell.meanCn0DbHz, 1e-4f)
        assertEquals(40f, cell.maxCn0DbHz)
        assertEquals(3, cell.samples)
        assertEquals(3, map.totalSamples)
    }

    @Test
    fun `untracked and unlocated signals are skipped`() {
        val map = ObstructionMap().onSnapshot(
            listOf(
                sat(1, 0f, 0f, cn0 = 35f), // unlocated
                sat(2, 100f, 20f, cn0 = 0f), // not tracked
                sat(3, 100f, 20f, cn0 = -1f),
                sat(4, 0f, 10f, cn0 = 25f), // due north is a real direction
            ),
        )
        assertEquals(1, map.totalSamples)
        assertEquals(0f, map.cells.single().azimuthFrom)
        assertEquals(10f, map.cells.single().elevationFrom)
    }

    @Test
    fun `each band of a dual-frequency satellite counts separately`() {
        val map = ObstructionMap().onSnapshot(
            listOf(sat(5, 200f, 60f, cn0 = 40f, freq = 1575.42e6f), sat(5, 200f, 60f, cn0 = 34f, freq = 1176.45e6f)),
        )
        val cell = map.cells.single()
        assertEquals(2, cell.samples)
        assertEquals(37f, cell.meanCn0DbHz, 1e-4f)
        assertEquals(40f, cell.maxCn0DbHz)
    }

    @Test
    fun `an empty map has no cells`() {
        assertTrue(ObstructionMap().cells.isEmpty())
        assertEquals(0, ObstructionMap().totalSamples)
    }
}
