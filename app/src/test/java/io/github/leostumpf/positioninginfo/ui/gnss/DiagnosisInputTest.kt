// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.domain.Constellation
import io.github.leostumpf.positioninginfo.domain.PowerSaveLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisInputTest {

    private fun sat(svid: Int, cn0: Float, used: Boolean = true, az: Float = svid * 40f, el: Float = 30f + svid, mhz: Float = 1575.42f) =
        SatelliteInfo(
            svid = svid, constellation = Constellation.GPS, cn0DbHz = cn0,
            elevationDegrees = el, azimuthDegrees = az, usedInFix = used,
            hasAlmanac = true, hasEphemeris = true, carrierFrequencyHz = mhz * 1_000_000f,
        )

    private fun input(vararg sats: SatelliteInfo, fix: SpeedFix? = null) = diagnosisInput(
        snapshot = GnssSnapshot(satellites = sats.toList(), hasReported = true),
        fix = fix, gpsEnabled = true, powerSave = PowerSaveLocation.UNRESTRICTED,
        airplaneMode = false, dataConnection = true, searchingMs = 3_000L, firstFixMs = null,
    )

    @Test
    fun `a satellite on two bands counts once`() {
        val i = input(sat(1, 40f), sat(1, 33f, mhz = 1176.45f), sat(2, 20f, used = false))
        assertEquals(2, i.satellitesHeard)
        assertEquals(1, i.satellitesStrong)
        assertEquals(1, i.usedInFix)
    }

    @Test
    fun `geometry needs real sky positions`() {
        val placed = input(sat(1, 40f), sat(2, 40f), sat(3, 40f), sat(4, 40f), sat(5, 40f))
        assertNotNull(placed.pdop)
        // Satellites the chip reports at 0°/0° have no known position and are left out.
        val unplaced = input(*(1..5).map { sat(it, 40f, az = 0f, el = 0f) }.toTypedArray())
        assertNull(unplaced.pdop)
    }

    @Test
    fun `a simulated fix is flagged`() {
        val mock = SpeedFix(speedMps = 0f, speedAccuracyMps = null, horizontalAccuracyM = 5f, elapsedRealtimeMs = 0L, isMock = true)
        assertTrue(input(sat(1, 40f), fix = mock).isMock)
    }
}
