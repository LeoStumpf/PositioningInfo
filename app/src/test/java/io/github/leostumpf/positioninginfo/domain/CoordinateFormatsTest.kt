// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateFormatsTest {

    // UTM references come from an independent implementation of Snyder's USGS series
    // (Professional Paper 1395, eqs. 8-9 to 8-10), which agrees with Krüger to well under a
    // decimetre this close to the central meridian. Half a metre leaves room for that.
    private fun assertUtm(lat: Double, lon: Double, zone: Int, band: Char, e: Double, n: Double) {
        val utm = CoordinateFormats.utm(lat, lon)
        assertNotNull(utm)
        assertEquals(zone, utm!!.zone)
        assertEquals(band, utm.band)
        assertEquals(e, utm.easting, 0.5)
        assertEquals(n, utm.northing, 0.5)
    }

    @Test
    fun `decimal degrees use hemisphere letters`() {
        assertEquals("52.516275° N, 13.377704° E", CoordinateFormats.decimal(52.516275, 13.377704))
        assertEquals("33.856800° S, 70.500000° W", CoordinateFormats.decimal(-33.8568, -70.5))
    }

    @Test
    fun `dms rounds to a tenth of a second`() {
        assertEquals("52°30′58.6″ N, 13°22′39.7″ E", CoordinateFormats.dms(52.516275, 13.377704))
        assertEquals("33°51′24.5″ S, 151°12′55.1″ E", CoordinateFormats.dms(-33.8568, 151.2153))
    }

    @Test
    fun `dms carries 59·96 seconds into the next minute and degree`() {
        // 10°59′59.96″ = 10.9999889°: must read 11°0′0.0″, never 10°59′60.0″.
        assertEquals("11°0′0.0″ N, 0°0′0.0″ E", CoordinateFormats.dms(10.0 + 59.0 / 60 + 59.96 / 3600, 0.0))
    }

    @Test
    fun `utm on the equator at a central meridian is the false origin`() {
        assertUtm(0.0, 3.0, 31, 'N', 500_000.0, 0.0)
    }

    @Test
    fun `utm matches reference values`() {
        assertUtm(52.516275, 13.377704, 33, 'U', 389_918.042, 5_819_699.133)
        assertUtm(40.7128, -74.0060, 18, 'T', 583_959.372, 4_507_350.998)
        assertUtm(-80.0, -179.0, 1, 'C', 461_235.942, 1_117_747.830)
        assertUtm(83.9, 178.0, 60, 'X', 511_863.269, 9_317_033.098)
        assertEquals("33U 389918 5819699", CoordinateFormats.utm(52.516275, 13.377704).toString())
    }

    @Test
    fun `southern hemisphere adds the false northing`() {
        assertUtm(-33.8568, 151.2153, 56, 'H', 334_900.570, 6_252_288.753)
    }

    @Test
    fun `south-west Norway belongs to the widened zone 32`() {
        // 4°E is zone 31 by the 6° rule; band V between 3° and 12° is zone 32.
        assertUtm(60.5, 4.0, 32, 'V', 225_510.348, 6_717_531.156)
    }

    @Test
    fun `Svalbard uses the odd zones only`() {
        // 10°E would be zone 32; above 72° zones 32, 34 and 36 do not exist.
        assertUtm(78.0, 10.0, 33, 'X', 384_085.475, 8_663_320.202)
        assertEquals(31, CoordinateFormats.utm(78.0, 8.9)!!.zone)
        assertEquals(35, CoordinateFormats.utm(78.0, 21.0)!!.zone)
        assertEquals(37, CoordinateFormats.utm(78.0, 40.0)!!.zone)
    }

    @Test
    fun `utm is undefined near the poles`() {
        assertNull(CoordinateFormats.utm(85.0, 10.0))
        assertNull(CoordinateFormats.utm(-80.5, 10.0))
        assertNull(CoordinateFormats.mgrs(85.0, 10.0))
    }

    @Test
    fun `mgrs names the 100 km square and truncates the digits`() {
        // Zone 33: column set S..Z, easting 3xx km -> U; odd zone rows from A, 58 % 20 -> U.
        assertEquals("33U UU 89918 19699", CoordinateFormats.mgrs(52.516275, 13.377704))
        // Zone 18: set S..Z, 5xx km -> W; even zone rows shifted by 5: (45 + 5) % 20 -> L.
        assertEquals("18T WL 83959 07350", CoordinateFormats.mgrs(40.7128, -74.0060))
        // Zone 56: set J..R, 3xx km -> L; (62 + 5) % 20 = 7 -> H.
        assertEquals("56H LH 34900 52288", CoordinateFormats.mgrs(-33.8568, 151.2153))
    }

    @Test
    fun `plus code matches the official test data`() {
        assertEquals("8FVC2222+22", CoordinateFormats.plusCode(47.0000625, 8.0000625))
    }

    @Test
    fun `plus codes derived from the spec`() {
        // (20.3700625 + 90) * 8000 = 882960.5 -> 882960 = base 20 digits 5,10,7,8,0 -> 7 G 9 C 2
        // (2.7821875 + 180) * 8000 = 1462257.5 -> 1462257 = 9,2,15,12,17 -> F 4 Q J V
        assertEquals("7FG49QCJ+2V", CoordinateFormats.plusCode(20.3700625, 2.7821875))
        // (-41.2730625 + 90) * 8000 = 389815.5 -> 389815 = 2,8,14,10,15 -> 4 C P G Q
        // (174.7859375 + 180) * 8000 = 2838287.5 -> 2838287 = 17,14,15,14,7 -> V P Q P 9
        assertEquals("4VCPPQGP+Q9", CoordinateFormats.plusCode(-41.2730625, 174.7859375))
    }

    @Test
    fun `plus code normalises longitude and keeps the pole inside a cell`() {
        assertEquals("8FVC2222+22", CoordinateFormats.plusCode(47.0000625, 368.0000625))
        // Latitude 90 moves down one 1/8000° cell: 1439999 = 8,19,19,19,19 -> C X X X X.
        assertEquals("CFX2X2X2+X2", CoordinateFormats.plusCode(90.0, 0.0))
        assertEquals("CFX2X2X2+X2", CoordinateFormats.plusCode(95.0, 0.0))
    }

    @Test
    fun `maidenhead locator has six characters`() {
        assertEquals("JO62qm", CoordinateFormats.maidenhead(52.52, 13.40))
        assertEquals("JJ00aa", CoordinateFormats.maidenhead(0.0, 0.0))
        assertEquals("QF56od", CoordinateFormats.maidenhead(-33.8568, 151.2153))
        assertEquals("RR99xx", CoordinateFormats.maidenhead(90.0, 179.9999999))
        assertTrue(CoordinateFormats.maidenhead(-90.0, -180.0) == "AA00aa")
    }
}
