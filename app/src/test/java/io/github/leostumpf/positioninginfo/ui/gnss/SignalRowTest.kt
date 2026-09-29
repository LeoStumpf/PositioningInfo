// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import io.github.leostumpf.positioninginfo.domain.Constellation
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalRowTest {

    private fun signal(svid: Int, carrierHz: Float?, constellation: Constellation = Constellation.GPS) =
        SatelliteInfo(
            svid = svid,
            constellation = constellation,
            cn0DbHz = 30f,
            elevationDegrees = 45f,
            azimuthDegrees = 180f,
            usedInFix = true,
            hasAlmanac = true,
            hasEphemeris = true,
            carrierFrequencyHz = carrierHz,
        )

    private fun List<SignalRow>.assertKeysUnique() =
        assertEquals(map { it.key }.distinct().size, size)

    @Test
    fun `one satellite on two bands gets two distinct keys`() {
        val rows = SignalRow.keyed(listOf(signal(7, 1_575.42e6f), signal(7, 1_176.45e6f)))
        rows.assertKeysUnique()
    }

    @Test
    fun `a repeated satellite without carrier frequency still gets distinct keys`() {
        // A misbehaving driver must not be able to crash the list.
        val rows = SignalRow.keyed(listOf(signal(7, null), signal(7, null), signal(7, null)))
        rows.assertKeysUnique()
    }

    @Test
    fun `keys are stable across sweeps for the same signals`() {
        val sweep = listOf(signal(7, 1_575.42e6f), signal(7, 1_176.45e6f), signal(3, null))
        assertEquals(SignalRow.keyed(sweep).map { it.key }, SignalRow.keyed(sweep).map { it.key })
    }
}
