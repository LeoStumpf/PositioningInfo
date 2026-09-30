// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.sky

import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import io.github.leostumpf.positioninginfo.domain.Constellation
import io.github.leostumpf.positioninginfo.domain.SkyTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyUiStateTest {

    private fun sat(svid: Int, used: Boolean, cn0: Float = 35f) = SatelliteInfo(
        svid = svid, constellation = Constellation.GALILEO, cn0DbHz = cn0,
        elevationDegrees = 40f, azimuthDegrees = 120f + svid, usedInFix = used,
        hasAlmanac = true, hasEphemeris = true, carrierFrequencyHz = null,
    )

    @Test
    fun `satellites in the fix are drawn last, on top`() {
        val tracker = SkyTracker().onSnapshot(listOf(sat(3, used = true), sat(7, used = false)), nowMs = 0L)
        val markers = SkyUiState.from(tracker, nowMs = 0L).markers
        assertEquals(listOf("E07", "E03"), markers.map { it.label })
        assertTrue(markers.last().usedInFix)
        assertNotNull(markers.last().current)
    }

    @Test
    fun `a satellite gone from the list keeps only its trail`() {
        val later = SkyTracker()
            .onSnapshot(listOf(sat(3, used = true)), nowMs = 0L)
            .onSnapshot(emptyList(), nowMs = SkyTracker.LOST_AFTER_MS + 1_000L)
        val marker = SkyUiState.from(later, nowMs = SkyTracker.LOST_AFTER_MS + 1_000L).markers.single()
        assertNull(marker.current)
        assertFalse(marker.trail.isEmpty())
    }
}
