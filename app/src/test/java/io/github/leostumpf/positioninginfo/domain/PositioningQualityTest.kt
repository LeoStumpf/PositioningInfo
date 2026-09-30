// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositioningQualityTest {

    private var nextSvid = 1

    private fun sat(
        constellation: Constellation = Constellation.GPS,
        used: Boolean = true,
        carrierMhz: Double? = 1575.42,
        svid: Int? = null,
    ) = SatelliteInfo(
        svid = svid ?: nextSvid++,
        constellation = constellation,
        cn0DbHz = 35f,
        elevationDegrees = 45f,
        azimuthDegrees = 180f,
        usedInFix = used,
        hasAlmanac = true,
        hasEphemeris = true,
        carrierFrequencyHz = carrierMhz?.let { (it * 1_000_000).toFloat() },
    )

    private fun snapshot(vararg sats: SatelliteInfo) = GnssSnapshot(satellites = sats.toList(), hasReported = true)

    @Test
    fun `too few satellites in the fix means no positioning at all`() {
        val quality = PositioningQuality.from(snapshot(sat(), sat(), sat()))
        assertEquals(ResolutionClass.NO_FIX, quality.resolution)
    }

    @Test
    fun `one constellation on one band is the weakest usable case`() {
        val quality = PositioningQuality.from(snapshot(*Array(5) { sat() }))
        assertEquals(ResolutionClass.SINGLE_CONSTELLATION, quality.resolution)
        assertFalse(quality.dualFrequency)
    }

    @Test
    fun `mixing constellations improves the expected resolution`() {
        val quality = PositioningQuality.from(
            snapshot(
                sat(Constellation.GPS),
                sat(Constellation.GPS),
                sat(Constellation.GALILEO),
                sat(Constellation.GLONASS),
            ),
        )
        assertEquals(ResolutionClass.MULTI_CONSTELLATION, quality.resolution)
        assertEquals(
            listOf(Constellation.GPS, Constellation.GLONASS, Constellation.GALILEO),
            quality.constellationsInUse,
        )
    }

    @Test
    fun `hearing L1 and L5 together counts as dual frequency`() {
        val quality = PositioningQuality.from(
            snapshot(
                sat(carrierMhz = 1575.42, svid = 1),
                sat(carrierMhz = 1575.42, svid = 2),
                sat(carrierMhz = 1575.42, svid = 3),
                sat(carrierMhz = 1575.42, svid = 4),
                sat(carrierMhz = 1176.45, svid = 1),
                sat(carrierMhz = 1176.45, svid = 2),
            ),
        )
        assertTrue(quality.dualFrequency)
        assertEquals(ResolutionClass.DUAL_FREQUENCY, quality.resolution)
        assertEquals(listOf(SignalBand.L1, SignalBand.L5), quality.bandsInUse)
    }

    @Test
    fun `L1 and L5 from different satellites is not dual frequency`() {
        val quality = PositioningQuality.from(
            snapshot(
                sat(carrierMhz = 1575.42, svid = 1),
                sat(carrierMhz = 1575.42, svid = 2),
                sat(carrierMhz = 1176.45, svid = 3),
                sat(carrierMhz = 1176.45, svid = 4),
            ),
        )
        assertFalse(quality.dualFrequency)
    }

    @Test
    fun `L5 alone is not dual frequency`() {
        // Two bands are needed to cancel ionospheric delay; one modern band is still one band.
        val quality = PositioningQuality.from(snapshot(*Array(4) { sat(carrierMhz = 1176.45) }))
        assertFalse(quality.dualFrequency)
    }

    @Test
    fun `augmentation used in the fix is recognised and upgrades the estimate`() {
        val quality = PositioningQuality.from(
            snapshot(
                sat(),
                sat(),
                sat(),
                sat(Constellation.SBAS, svid = 123),
            ),
        )
        assertTrue(quality.sbasUsedInFix)
        assertEquals(ResolutionClass.AUGMENTED, quality.resolution)
        assertEquals(listOf(SbasSystem.EGNOS), quality.sbasInView)
    }

    @Test
    fun `dual frequency plus augmentation is the best case`() {
        val quality = PositioningQuality.from(
            snapshot(
                sat(carrierMhz = 1575.42, svid = 1),
                sat(carrierMhz = 1575.42, svid = 2),
                sat(carrierMhz = 1575.42, svid = 3),
                sat(carrierMhz = 1176.45, svid = 1),
                sat(Constellation.SBAS, svid = 131, carrierMhz = 1575.42),
            ),
        )
        assertEquals(ResolutionClass.DUAL_FREQUENCY_AUGMENTED, quality.resolution)
    }

    @Test
    fun `augmentation in view but unused does not improve the estimate`() {
        // Seeing EGNOS overhead is not the same as applying its corrections.
        val quality = PositioningQuality.from(
            snapshot(
                sat(),
                sat(),
                sat(),
                sat(),
                sat(Constellation.SBAS, svid = 123, used = false),
            ),
        )
        assertFalse(quality.sbasUsedInFix)
        assertEquals(listOf(SbasSystem.EGNOS), quality.sbasInView)
        assertEquals(ResolutionClass.SINGLE_CONSTELLATION, quality.resolution)
    }

    @Test
    fun `augmentation is not counted as a positioning constellation`() {
        // SBAS satellites correct the others' signals; they are not a navigation system
        // in their own right, so they must not inflate the constellation count.
        val quality = PositioningQuality.from(
            snapshot(
                sat(Constellation.GPS),
                sat(Constellation.GPS),
                sat(Constellation.GPS),
                sat(Constellation.SBAS, svid = 123),
            ),
        )
        assertEquals(listOf(Constellation.GPS), quality.constellationsInUse)
    }

    @Test
    fun `a receiver reporting no carrier frequencies says so instead of guessing`() {
        val quality = PositioningQuality.from(snapshot(*Array(5) { sat(carrierMhz = null) }))
        assertTrue(quality.bandsUnavailable)
        assertTrue(quality.bandsInUse.isEmpty())
        assertFalse(quality.dualFrequency)
    }

    @Test
    fun `satellites merely visible do not count towards the technique in use`() {
        // The accuracy of the reported position depends on what produced it, not on what
        // else happens to be overhead.
        val quality = PositioningQuality.from(
            snapshot(
                *Array(4) { sat(carrierMhz = 1575.42) },
                sat(carrierMhz = 1176.45, used = false),
            ),
        )
        assertFalse(quality.dualFrequency)
        assertEquals(listOf(SignalBand.L1), quality.bandsInUse)
    }

    @Test
    fun `four signals from two dual-frequency satellites are not a fix`() {
        val quality = PositioningQuality.from(
            snapshot(
                sat(svid = 1, carrierMhz = 1575.42),
                sat(svid = 1, carrierMhz = 1176.45),
                sat(svid = 2, carrierMhz = 1575.42),
                sat(svid = 2, carrierMhz = 1176.45),
            ),
        )
        assertEquals(ResolutionClass.NO_FIX, quality.resolution)
        assertEquals(listOf(SignalBand.L1, SignalBand.L5), quality.bandsInUse)
    }

    @Test
    fun `four dual-frequency satellites are a dual-frequency fix`() {
        val quality = PositioningQuality.from(
            snapshot(
                *(1..4).flatMap { svid ->
                    listOf(sat(svid = svid, carrierMhz = 1575.42), sat(svid = svid, carrierMhz = 1176.45))
                }.toTypedArray(),
            ),
        )
        assertEquals(ResolutionClass.DUAL_FREQUENCY, quality.resolution)
    }
}
