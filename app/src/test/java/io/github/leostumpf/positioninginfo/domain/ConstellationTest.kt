// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import android.location.GnssStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConstellationTest {

    @Test
    fun `maps the platform constellation constants`() {
        // Pinned against the real GnssStatus constants rather than repeating the literals,
        // so a platform renumbering would fail here instead of mislabelling satellites.
        assertEquals(Constellation.GPS, Constellation.fromAndroidType(GnssStatus.CONSTELLATION_GPS))
        assertEquals(Constellation.SBAS, Constellation.fromAndroidType(GnssStatus.CONSTELLATION_SBAS))
        assertEquals(Constellation.GLONASS, Constellation.fromAndroidType(GnssStatus.CONSTELLATION_GLONASS))
        assertEquals(Constellation.QZSS, Constellation.fromAndroidType(GnssStatus.CONSTELLATION_QZSS))
        assertEquals(Constellation.BEIDOU, Constellation.fromAndroidType(GnssStatus.CONSTELLATION_BEIDOU))
        assertEquals(Constellation.GALILEO, Constellation.fromAndroidType(GnssStatus.CONSTELLATION_GALILEO))
        assertEquals(Constellation.IRNSS, Constellation.fromAndroidType(GnssStatus.CONSTELLATION_IRNSS))
    }

    @Test
    fun `unknown and out-of-range types fall back rather than throwing`() {
        assertEquals(Constellation.UNKNOWN, Constellation.fromAndroidType(GnssStatus.CONSTELLATION_UNKNOWN))
        assertEquals(Constellation.UNKNOWN, Constellation.fromAndroidType(99))
        assertEquals(Constellation.UNKNOWN, Constellation.fromAndroidType(-1))
    }
}
