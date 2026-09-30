// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SbasSystemTest {

    @Test
    fun `identifies the regional augmentation systems by PRN`() {
        assertEquals(SbasSystem.EGNOS, SbasSystem.fromSvid(123))
        assertEquals(SbasSystem.EGNOS, SbasSystem.fromSvid(136))
        assertEquals(SbasSystem.WAAS, SbasSystem.fromSvid(131))
        assertEquals(SbasSystem.WAAS, SbasSystem.fromSvid(138))
        assertEquals(SbasSystem.MSAS, SbasSystem.fromSvid(129))
        assertEquals(SbasSystem.GAGAN, SbasSystem.fromSvid(127))
        assertEquals(SbasSystem.SDCM, SbasSystem.fromSvid(125))
        assertEquals(SbasSystem.BDSBAS, SbasSystem.fromSvid(130))
        assertEquals(SbasSystem.KASS, SbasSystem.fromSvid(134))
        assertEquals(SbasSystem.SOUTHPAN, SbasSystem.fromSvid(122))
    }

    @Test
    fun `every allocated PRN belongs to its system and no other`() {
        // The allocation table, written out independently of the mapping: a PRN moved to the
        // wrong system, or dropped, fails here. The whole range is swept, so a PRN mapped that
        // the table does not list fails too.
        val allocation = mapOf(
            SbasSystem.EGNOS to listOf(120, 121, 123, 124, 126, 136),
            SbasSystem.SOUTHPAN to listOf(122),
            SbasSystem.SDCM to listOf(125, 140, 141),
            SbasSystem.GAGAN to listOf(127, 128, 132),
            SbasSystem.MSAS to listOf(129, 137),
            SbasSystem.BDSBAS to listOf(130, 143, 144),
            SbasSystem.WAAS to listOf(131, 133, 135, 138),
            SbasSystem.KASS to listOf(134),
        )
        val expected = allocation.flatMap { (system, prns) -> prns.map { it to system } }.toMap()
        for (prn in 120..158) {
            assertEquals("PRN $prn", expected[prn] ?: SbasSystem.UNKNOWN_SBAS, SbasSystem.fromSvid(prn))
        }
    }

    @Test
    fun `unallocated PRNs fall back rather than being misattributed`() {
        assertEquals(SbasSystem.UNKNOWN_SBAS, SbasSystem.fromSvid(150))
        assertEquals(SbasSystem.UNKNOWN_SBAS, SbasSystem.fromSvid(1))
    }
}
