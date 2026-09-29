// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

import io.github.leostumpf.gpstools.data.model.GnssSnapshot
import io.github.leostumpf.gpstools.data.model.SatelliteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmanacStatusTest {

    private var nextSvid = 1

    private fun sat(
        constellation: Constellation = Constellation.GPS,
        almanac: Boolean = false,
        ephemeris: Boolean = false,
        used: Boolean = false,
        cn0: Float = 30f,
        carrierHz: Float? = null,
        svid: Int? = null,
    ) = SatelliteInfo(
        svid = svid ?: nextSvid++,
        constellation = constellation,
        cn0DbHz = cn0,
        elevationDegrees = 45f,
        azimuthDegrees = 180f,
        usedInFix = used,
        hasAlmanac = almanac,
        hasEphemeris = ephemeris,
        carrierFrequencyHz = carrierHz,
    )

    private fun snapshot(vararg sats: SatelliteInfo) =
        GnssSnapshot(satellites = sats.toList(), hasReported = true)

    @Test
    fun `nothing heard from the receiver yet is unknown, not cold`() {
        val status = AlmanacStatus.from(GnssSnapshot.EMPTY)
        assertEquals(AlmanacReadiness.UNKNOWN, status.readiness)
        assertEquals(0, status.visible)
    }

    @Test
    fun `ephemeris for four satellites means a fix in seconds`() {
        val status = AlmanacStatus.from(
            snapshot(*Array(4) { sat(almanac = true, ephemeris = true) }),
        )
        assertEquals(AlmanacReadiness.HOT, status.readiness)
        assertEquals(4, status.withEphemeris)
    }

    @Test
    fun `almanac without enough ephemeris is a warm start`() {
        val status = AlmanacStatus.from(
            snapshot(
                sat(almanac = true, ephemeris = true),
                sat(almanac = true),
                sat(almanac = true),
                sat(almanac = true),
                sat(almanac = true),
            ),
        )
        assertEquals(AlmanacReadiness.WARM, status.readiness)
        assertEquals(5, status.withAlmanac)
        assertEquals(1, status.withEphemeris)
    }

    @Test
    fun `too little orbital data is a cold start`() {
        val status = AlmanacStatus.from(snapshot(sat(almanac = true), sat(), sat()))
        assertEquals(AlmanacReadiness.COLD, status.readiness)
    }

    @Test
    fun `three satellites of ephemeris is still not enough for a fix`() {
        // Four are needed for a three-dimensional fix; three is a warm start at best.
        val status = AlmanacStatus.from(
            snapshot(*Array(3) { sat(almanac = true, ephemeris = true) }),
        )
        assertEquals(AlmanacReadiness.WARM, status.readiness)
    }

    @Test
    fun `a device fixing with no ephemeris on record has an unreliable ephemeris flag`() {
        // Computing a fix without ephemeris is impossible, so the flag is unimplemented.
        val status = AlmanacStatus.from(snapshot(*Array(6) { sat(used = true) }))
        assertTrue(status.ephemerisUnavailable)
        assertEquals(6, status.usedInFix)
    }

    @Test
    fun `a receiver that is actually fixing is hot whatever its flags claim`() {
        // Observed behaviour outranks reported state: it is demonstrably ready.
        val status = AlmanacStatus.from(snapshot(*Array(6) { sat(used = true) }))
        assertEquals(AlmanacReadiness.HOT, status.readiness)
    }

    @Test
    fun `an unreliable ephemeris flag does not suppress the almanac figures`() {
        // Many phones report the almanac correctly while omitting the ephemeris; the
        // almanac is the number this screen exists to show, so it must survive.
        val status = AlmanacStatus.from(
            snapshot(*Array(6) { sat(almanac = true, used = true) }),
        )
        assertTrue(status.ephemerisUnavailable)
        assertEquals(6, status.withAlmanac)
        assertEquals(6, status.perConstellation.single().almanac)
    }

    @Test
    fun `a genuine cold start is not mistaken for an unreporting device`() {
        // Nothing used in fix, so there is no contradiction to detect: this really is cold.
        val status = AlmanacStatus.from(snapshot(sat(), sat(), sat()))
        assertEquals(AlmanacReadiness.COLD, status.readiness)
        assertFalse(status.ephemerisUnavailable)
    }

    @Test
    fun `a partial fix below the four-satellite threshold is not called hot`() {
        val status = AlmanacStatus.from(
            snapshot(*Array(3) { sat(almanac = true, used = true) }),
        )
        assertFalse(status.ephemerisUnavailable)
        assertEquals(AlmanacReadiness.COLD, status.readiness)
    }

    @Test
    fun `constellations are summarised separately`() {
        val status = AlmanacStatus.from(
            snapshot(
                sat(Constellation.GPS, almanac = true, ephemeris = true, used = true),
                sat(Constellation.GPS, almanac = true),
                sat(Constellation.GALILEO, almanac = true, ephemeris = true),
                sat(Constellation.GLONASS),
            ),
        )

        val gps = status.perConstellation.first { it.constellation == Constellation.GPS }
        assertEquals(2, gps.visible)
        assertEquals(2, gps.almanac)
        assertEquals(1, gps.ephemeris)
        assertEquals(1, gps.usedInFix)

        val glonass = status.perConstellation.first { it.constellation == Constellation.GLONASS }
        assertEquals(1, glonass.visible)
        assertEquals(0, glonass.almanac)
    }

    @Test
    fun `constellations are ordered by size so the table does not jitter`() {
        val status = AlmanacStatus.from(
            snapshot(
                sat(Constellation.GLONASS),
                sat(Constellation.GPS),
                sat(Constellation.GPS),
                sat(Constellation.GPS),
                sat(Constellation.GALILEO),
                sat(Constellation.GALILEO),
            ),
        )
        assertEquals(
            listOf(Constellation.GPS, Constellation.GALILEO, Constellation.GLONASS),
            status.perConstellation.map { it.constellation },
        )
    }

    @Test
    fun `equal-sized constellations keep a stable alphabetical order`() {
        val first = AlmanacStatus.from(snapshot(sat(Constellation.GPS), sat(Constellation.BEIDOU)))
        val second = AlmanacStatus.from(snapshot(sat(Constellation.BEIDOU), sat(Constellation.GPS)))
        assertEquals(
            first.perConstellation.map { it.constellation },
            second.perConstellation.map { it.constellation },
        )
    }

    @Test
    fun `a few ephemerides without an almanac still beat a cold start`() {
        // Holding precise orbits for two satellites means the receiver is tracking and
        // decoding, not searching blindly, even though it cannot fix yet.
        val status = AlmanacStatus.from(snapshot(sat(ephemeris = true), sat(ephemeris = true)))
        assertEquals(AlmanacReadiness.WARM, status.readiness)
    }

    @Test
    fun `a satellite heard on two bands counts once`() {
        // Dual-frequency receivers report each satellite once per carrier.
        val status = AlmanacStatus.from(
            snapshot(
                *(1..3).flatMap { svid ->
                    listOf(
                        sat(svid = svid, almanac = true, ephemeris = true, used = true, carrierHz = L1_HZ),
                        sat(svid = svid, almanac = true, ephemeris = true, used = true, carrierHz = L5_HZ),
                    )
                }.toTypedArray(),
            ),
        )
        assertEquals(3, status.visible)
        assertEquals(3, status.withAlmanac)
        assertEquals(3, status.withEphemeris)
        assertEquals(3, status.usedInFix)
        assertEquals(3, status.perConstellation.single().visible)
    }

    @Test
    fun `two dual-frequency satellites are not enough to be ready`() {
        // Four signals from two satellites is still two satellites, short of a fix.
        val status = AlmanacStatus.from(
            snapshot(
                sat(svid = 1, almanac = true, ephemeris = true, used = true, carrierHz = L1_HZ),
                sat(svid = 1, almanac = true, ephemeris = true, used = true, carrierHz = L5_HZ),
                sat(svid = 2, almanac = true, ephemeris = true, used = true, carrierHz = L1_HZ),
                sat(svid = 2, almanac = true, ephemeris = true, used = true, carrierHz = L5_HZ),
            ),
        )
        assertEquals(AlmanacReadiness.WARM, status.readiness)
        assertFalse(status.ephemerisUnavailable)
    }

    @Test
    fun `the same svid in two constellations is two satellites`() {
        val status = AlmanacStatus.from(
            snapshot(
                sat(Constellation.GPS, svid = 5),
                sat(Constellation.GALILEO, svid = 5),
            ),
        )
        assertEquals(2, status.visible)
    }

    private companion object {
        const val L1_HZ = 1_575.42e6f
        const val L5_HZ = 1_176.45e6f
    }
}
