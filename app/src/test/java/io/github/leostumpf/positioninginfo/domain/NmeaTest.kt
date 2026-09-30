// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaTest {

    private val gga = "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"
    private val ggaSouthWest = "\$GPGGA,092750.000,3356.5160,S,15112.7820,W,1,8,1.03,61.7,M,55.2,M,,*68"
    private val ggaNoFix = "\$GNGGA,,,,,,0,00,99.99,,,,,,*56"
    private val gst = "\$GPGST,172814.0,0.006,0.023,0.020,273.6,0.023,0.020,0.031*6A"
    private val gsaGlonass = "\$GNGSA,A,3,80,71,73,79,69,,,,,,,,1.83,1.09,1.47,2*09"
    private val gsaEmpty = "\$GNGSA,A,3,,,,,,,,,,,,,,,,1*1F"
    private val gsaOld = "\$GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39"
    private val rmc = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
    private val rmcNoFix = "\$GNRMC,,V,,,,,,,,,,N*4D"
    private val gsv = "\$GPGSV,3,1,11,03,03,111,00,04,15,270,00,06,01,010,00,13,06,292,00*74"
    private val proprietary = "\$PGRME,15.0,M,45.0,M,25.0,M*1C"

    @Test
    fun `a GGA is decoded to signed decimal degrees`() {
        val s = NmeaParser.parse(gga) as Gga
        assertEquals("GP", s.talker)
        assertEquals("GGA", s.type)
        assertEquals(48.0 + 7.038 / 60, s.latitude!!, 1e-9)
        assertEquals(11.0 + 31.0 / 60, s.longitude!!, 1e-9)
        assertEquals(1, s.fixQuality)
        assertEquals(8, s.satellites)
        assertEquals(0.9, s.hdop!!, 1e-9)
        assertEquals(545.4, s.altitudeMslM!!, 1e-9)
        assertEquals(46.9, s.geoidSeparationM!!, 1e-9)
    }

    @Test
    fun `southern and western hemispheres are negative`() {
        val s = NmeaParser.parse(ggaSouthWest) as Gga
        assertEquals(-(33.0 + 56.516 / 60), s.latitude!!, 1e-9)
        assertEquals(-(151.0 + 12.782 / 60), s.longitude!!, 1e-9)
    }

    @Test
    fun `empty fields without a fix become null`() {
        val s = NmeaParser.parse(ggaNoFix) as Gga
        assertEquals("GN", s.talker)
        assertNull(s.latitude)
        assertNull(s.longitude)
        assertEquals(0, s.fixQuality)
        assertEquals(0, s.satellites)
        assertEquals(99.99, s.hdop!!, 1e-9)
        assertNull(s.altitudeMslM)
        assertNull(s.geoidSeparationM)

        val r = NmeaParser.parse(rmcNoFix) as Rmc
        assertFalse(r.valid)
        assertNull(r.speedKnots)
        assertNull(r.courseDeg)
    }

    @Test
    fun `trailing CR LF is tolerated`() {
        assertTrue(NmeaParser.parse("$gga\r\n") is Gga)
    }

    @Test
    fun `a wrong or missing checksum is rejected`() {
        assertNull(NmeaParser.parse(gga.dropLast(2) + "48"))
        assertNull(NmeaParser.parse(gga.substringBefore('*')))
        // One corrupted character, checksum untouched.
        assertNull(NmeaParser.parse(gga.replace("4807.038", "4807.039")))
    }

    @Test
    fun `lowercase checksum digits are accepted`() {
        assertTrue(NmeaParser.parse(gst.dropLast(2) + "6a") is Gst)
    }

    @Test
    fun `a non-numeric field makes the sentence malformed`() {
        // Checksum is correct for the corrupted text, so only the field check can catch it.
        assertNull(NmeaParser.parse("\$GPGGA,123519,48x7.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*0F"))
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(NmeaParser.parse(""))
        assertNull(NmeaParser.parse("hello"))
        assertNull(NmeaParser.parse("GPGGA,123519*47"))
    }

    @Test
    fun `a GST carries the receiver's own error estimate`() {
        val s = NmeaParser.parse(gst) as Gst
        assertEquals(0.006, s.rmsM!!, 1e-9)
        assertEquals(0.023, s.semiMajorM!!, 1e-9)
        assertEquals(0.020, s.semiMinorM!!, 1e-9)
        assertEquals(273.6, s.orientationDeg!!, 1e-9)
        assertEquals(0.023, s.latSigmaM!!, 1e-9)
        assertEquals(0.020, s.lonSigmaM!!, 1e-9)
        assertEquals(0.031, s.altSigmaM!!, 1e-9)
    }

    @Test
    fun `an NMEA 4_10 GSA carries its system id`() {
        val s = NmeaParser.parse(gsaGlonass) as Gsa
        assertEquals('A', s.mode)
        assertEquals(3, s.fixType)
        assertEquals(listOf(80, 71, 73, 79, 69), s.prns)
        assertEquals(1.83, s.pdop!!, 1e-9)
        assertEquals(1.09, s.hdop!!, 1e-9)
        assertEquals(1.47, s.vdop!!, 1e-9)
        assertEquals(2, s.systemId)
    }

    @Test
    fun `an older GSA has no system id and skips empty slots`() {
        val s = NmeaParser.parse(gsaOld) as Gsa
        assertEquals(listOf(4, 5, 9, 12, 24), s.prns)
        assertNull(s.systemId)
        assertEquals(2.5, s.pdop!!, 1e-9)
    }

    @Test
    fun `an RMC gives speed and course`() {
        val s = NmeaParser.parse(rmc) as Rmc
        assertTrue(s.valid)
        assertEquals(22.4, s.speedKnots!!, 1e-9)
        assertEquals(84.4, s.courseDeg!!, 1e-9)
    }

    @Test
    fun `other and proprietary sentences are kept by type`() {
        assertEquals(OtherSentence("GP", "GSV"), NmeaParser.parse(gsv))
        assertEquals(OtherSentence("P", "GRME"), NmeaParser.parse(proprietary))
    }

    @Test
    fun `state counts types and keeps the latest of each`() {
        val state = listOf(gga, gsv, gsv, gsaGlonass, gsaEmpty, gst, rmc, ggaSouthWest, "junk", gga.dropLast(1) + "0")
            .fold(NmeaState()) { acc, line -> acc.onLine(line) }

        assertEquals(10, state.total)
        assertEquals(2, state.rejected)
        assertEquals(mapOf("GGA" to 2, "GSV" to 2, "GSA" to 2, "GST" to 1, "RMC" to 1), state.counts)
        assertEquals(-(33.0 + 56.516 / 60), state.gga!!.latitude!!, 1e-9)
        // The empty GSA after the GLONASS one does not blank the DOP.
        assertEquals(2, state.gsa!!.systemId)
        assertEquals(1.83, state.gsa!!.pdop!!, 1e-9)
        assertEquals(0.031, state.gst!!.altSigmaM!!, 1e-9)
        assertTrue(state.rmc!!.valid)
    }

    private fun withChecksum(body: String): String =
        "\$" + body + "*" + "%02X".format(body.fold(0) { acc, c -> acc xor c.code })

    @Test
    fun `a signed checksum is rejected`() {
        val body = "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"
        val sum = body.fold(0) { acc, c -> acc xor c.code }
        assertNull(NmeaParser.parse("\$" + body + "*+" + (sum and 0xF).toString(16)))
    }

    @Test
    fun `coordinates out of range are malformed`() {
        val lat = NmeaParser.parse(withChecksum("GPGGA,123519,9959.900,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"))
        val lon = NmeaParser.parse(withChecksum("GPGGA,123519,4807.038,N,19959.000,E,1,08,0.9,545.4,M,46.9,M,,"))
        assertTrue(lat == null || lat !is Gga)
        assertTrue(lon == null || lon !is Gga)
    }
}
