// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

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
    fun `no PRN is claimed by two systems`() {
        // A duplicate in the mapping would silently shadow one service, so the whole
        // allocated range is swept rather than spot-checked.
        val assignments = (120..158).map { it to SbasSystem.fromSvid(it) }
        val named = assignments.filter { it.second != SbasSystem.UNKNOWN_SBAS }
        assertEquals(named.size, named.map { it.first }.distinct().size)
    }

    @Test
    fun `unallocated PRNs fall back rather than being misattributed`() {
        assertEquals(SbasSystem.UNKNOWN_SBAS, SbasSystem.fromSvid(150))
        assertEquals(SbasSystem.UNKNOWN_SBAS, SbasSystem.fromSvid(1))
    }
}
