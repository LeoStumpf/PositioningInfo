// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.cos
import kotlin.random.Random

class TripTest {

    /** Adds [p] with the climb height from [source], as the recorder does for a live fix. */
    private fun TripAccumulator.add(
        p: TripPoint,
        climbAltitudeM: Double?,
        source: ClimbSource = ClimbSource.BAROMETER,
    ) = add(p.copy(climbAltitudeM = climbAltitudeM, climbSource = source.takeIf { climbAltitudeM != null }))

    private val baseLat = 48.137
    private val baseLon = 11.575
    private val metresPerDegLat = Math.toRadians(1.0) * 6_371_000.0

    private fun point(
        tMs: Long,
        eastM: Double,
        northM: Double,
        speed: Float? = null,
        accuracy: Float? = 5f,
        alt: Double? = null,
    ) = TripPoint(
        timeUtcMs = tMs,
        latitude = baseLat + northM / metresPerDegLat,
        longitude = baseLon + eastM / (metresPerDegLat * cos(Math.toRadians(baseLat))),
        altitudeM = alt,
        speedMps = speed,
        accuracyM = accuracy,
    )

    @Test
    fun `empty accumulator has no data`() {
        val stats = TripAccumulator().stats
        assertEquals(0, stats.points)
        assertEquals(0.0, stats.distanceM, 0.0)
        assertNull(stats.maxSpeedMps)
        assertNull(stats.avgMovingSpeedMps)
    }

    @Test
    fun `standing jitter accumulates no distance`() {
        val random = Random(1)
        var trip = TripAccumulator()
        for (i in 0 until 600) {
            val p = point(i * 1_000L, random.nextDouble(-1.5, 1.5), random.nextDouble(-1.5, 1.5), speed = 0.1f)
            trip = trip.add(p, climbAltitudeM = null)
        }
        assertEquals(600, trip.stats.points)
        assertTrue("distance ${trip.stats.distanceM}", trip.stats.distanceM < 1.0)
        assertEquals(0L, trip.stats.movingTimeMs)
        assertEquals(599_000L, trip.stats.durationMs)
        assertNull(trip.stats.avgMovingSpeedMps)
    }

    @Test
    fun `a straight kilometre`() {
        var trip = TripAccumulator()
        for (i in 0..100) {
            trip = trip.add(point(i * 1_000L, 0.0, i * 10.0, speed = 10f), climbAltitudeM = null)
        }
        assertEquals(1_000.0, trip.stats.distanceM, 1.0)
        assertEquals(100_000L, trip.stats.movingTimeMs)
        assertEquals(100_000L, trip.stats.durationMs)
        assertEquals(10.0, trip.stats.avgMovingSpeedMps!!, 0.01)
        assertEquals(10f, trip.stats.maxSpeedMps!!, 0f)
    }

    @Test
    fun `slow walking without speed still accumulates once past the threshold`() {
        var trip = TripAccumulator()
        // 1 m every second, no reported speed: counted in 4 m steps (> 3 m), and moving.
        for (i in 0..40) trip = trip.add(point(i * 1_000L, i.toDouble(), 0.0, accuracy = 2f), null)
        assertEquals(40.0, trip.stats.distanceM, 0.5)
        assertEquals(40_000L, trip.stats.movingTimeMs)
    }

    @Test
    fun `inaccurate fixes contribute no distance`() {
        val trip = TripAccumulator()
            .add(point(0, 0.0, 0.0, speed = 5f), null)
            .add(point(1_000, 500.0, 0.0, speed = 5f, accuracy = 80f), null) // wild outlier
            .add(point(2_000, 0.0, 10.0, speed = 5f), null)
        assertEquals(10.0, trip.stats.distanceM, 0.1)
    }

    @Test
    fun `a gap travelled through counts as moving, so the average stays true`() {
        val trip = TripAccumulator()
            .add(point(0, 0.0, 0.0, speed = 10f), null)
            .add(point(1_000, 0.0, 10.0, speed = 10f), null)
            .add(point(121_000, 0.0, 1_210.0, speed = 10f), null) // two minutes in a tunnel
            .add(point(122_000, 0.0, 1_220.0, speed = 10f), null)
        assertEquals(122_000L, trip.stats.durationMs)
        assertEquals(122_000L, trip.stats.movingTimeMs)
        assertEquals(10.0, trip.stats.avgMovingSpeedMps!!, 0.5)
    }

    @Test
    fun `a gap spent standing still is not moving time`() {
        val trip = TripAccumulator()
            .add(point(0, 0.0, 0.0, speed = 10f), null)
            .add(point(1_000, 0.0, 10.0, speed = 10f), null)
            .add(point(601_000, 0.0, 10.0, speed = 0f), null) // ten minutes parked, app closed
            .add(point(602_000, 0.0, 20.0, speed = 10f), null)
        assertEquals(2_000L, trip.stats.movingTimeMs)
        assertEquals(10.0, trip.stats.avgMovingSpeedMps!!, 0.5)
    }

    @Test
    fun `ascent uses hysteresis so noise does not add up`() {
        val random = Random(42)
        var trip = TripAccumulator()
        var t = 0L
        // Climb 100 m in 0.5 m steps with ±1 m noise, then stand for a while.
        for (i in 0..200) {
            trip = trip.add(point(t, 0.0, 0.0), 400.0 + i * 0.5 + random.nextDouble(-1.0, 1.0))
            t += 1_000
        }
        repeat(300) {
            trip = trip.add(point(t, 0.0, 0.0), 500.0 + random.nextDouble(-1.0, 1.0))
            t += 1_000
        }
        // The reference may trail the true level by up to the hysteresis, never more.
        assertTrue("ascent ${trip.stats.ascentM}", trip.stats.ascentM in 96.0..103.0)
        assertEquals(0.0, trip.stats.descentM, 0.0)
    }

    @Test
    fun `altitude noise while standing adds no climb`() {
        val random = Random(3)
        var trip = TripAccumulator()
        for (i in 0 until 1_000) trip = trip.add(point(i * 1_000L, 0.0, 0.0), 500.0 + random.nextDouble(-1.4, 1.4))
        assertEquals(0.0, trip.stats.ascentM, 0.0)
        assertEquals(0.0, trip.stats.descentM, 0.0)
    }

    @Test
    fun `switching the altitude source is not a climb`() {
        var trip = TripAccumulator()
        // Standard atmosphere at 1030 hPa reads ~140 m low until the barometer is calibrated.
        for (i in 0 until 10) trip = trip.add(point(i * 1_000L, 0.0, 0.0), 360.0, ClimbSource.BAROMETER_STANDARD)
        for (i in 10 until 20) trip = trip.add(point(i * 1_000L, 0.0, 0.0), 500.0, ClimbSource.BAROMETER)
        // And GNSS height, when the barometer drops out, is off by its own offset.
        for (i in 20 until 30) trip = trip.add(point(i * 1_000L, 0.0, 0.0), 470.0, ClimbSource.GNSS)
        assertEquals(0.0, trip.stats.ascentM, 0.0)
        assertEquals(0.0, trip.stats.descentM, 0.0)
    }

    @Test
    fun `realistic GNSS height noise adds no climb`() {
        val random = Random(7)
        var trip = TripAccumulator()
        for (i in 0 until 1_000) {
            trip = trip.add(point(i * 1_000L, 0.0, 0.0), 500.0 + random.nextDouble(-4.5, 4.5), ClimbSource.GNSS)
        }
        assertEquals(0.0, trip.stats.ascentM, 0.0)
        assertEquals(0.0, trip.stats.descentM, 0.0)
    }

    @Test
    fun `non-finite or out-of-range values in the file are skipped`() {
        assertNull(TripCsv.decode("1000,NaN,11.5,500,1,5,500,B"))
        assertNull(TripCsv.decode("1000,48.1,Infinity,500,1,5,500,B"))
        assertNull(TripCsv.decode("1000,95.0,11.5,500,1,5,500,B"))
        assertNull(TripCsv.decode("1000,48.1,11.5,NaN,1,5,500,B"))
        assertNull(TripCsv.decode("1000,48.1,11.5,500,1,5,Infinity,B"))
        assertNull(TripCsv.decode("1000,48.1,11.5,500,1,5,500,X"))
    }

    @Test
    fun `descent is counted`() {
        var trip = TripAccumulator()
        for (i in 0..10) trip = trip.add(point(i * 1_000L, 0.0, 0.0), 100.0 - i * 5.0)
        assertEquals(50.0, trip.stats.descentM, 1e-9)
        assertEquals(0.0, trip.stats.ascentM, 0.0)
    }

    @Test
    fun `missing climb altitude is skipped`() {
        val trip = TripAccumulator()
            .add(point(0, 0.0, 0.0), 100.0)
            .add(point(1_000, 0.0, 0.0), null)
            .add(point(2_000, 0.0, 0.0), 110.0)
        assertEquals(10.0, trip.stats.ascentM, 1e-9)
    }

    @Test
    fun `CSV round trip keeps every field, nulls included`() {
        val full = TripPoint(
            timeUtcMs = 1_790_000_000_123L,
            latitude = 48.1370001,
            longitude = -11.5750002,
            altitudeM = 523.25,
            speedMps = 3.5f,
            accuracyM = 4.25f,
            climbAltitudeM = 488.5,
            climbSource = ClimbSource.BAROMETER_STANDARD,
        )
        val empty = TripPoint(0L, -33.9, 151.2, null, null, null)
        assertEquals(full, TripCsv.decode(TripCsv.encode(full)))
        assertEquals(empty, TripCsv.decode(TripCsv.encode(empty)))
        assertEquals("0,-33.9,151.2,,,,,", TripCsv.encode(empty))
        ClimbSource.entries.forEach { assertEquals(it, ClimbSource.fromCode(it.code)) }
    }

    @Test
    fun `CSV rejects malformed lines`() {
        assertNull(TripCsv.decode(""))
        assertNull(TripCsv.decode("123,48.1"))
        assertNull(TripCsv.decode("abc,48.1,11.5,,,,,"))
        assertNull(TripCsv.decode("1,48,1,11.5,,,,,"))
        // The earlier six-field format is not read as this one.
        assertNull(TripCsv.decode("1000,48.1,11.5,500,1,5"))
    }

    @Test
    fun `a trip file is read only under its own header`() {
        val line = TripCsv.encode(TripPoint(1_000L, 48.1, 11.5, 500.0, 1f, 5f, 480.0, ClimbSource.GNSS))
        assertEquals(1, TripCsv.decodeFile(listOf(TripCsv.HEADER, line)).size)
        assertTrue(TripCsv.decodeFile(listOf(line)).isEmpty())
        assertTrue(TripCsv.decodeFile(listOf("# Positioning Info trip, format 1", line)).isEmpty())
        assertTrue(TripCsv.decodeFile(emptyList()).isEmpty())
    }

    @Test
    fun `a reloaded trip counts the same climb as the live one`() {
        // Standard-atmosphere barometer until calibration, then the calibrated barometer: the
        // switch is no climb, and replaying the saved lines must give exactly the live totals.
        val points = (0 until 40).map { i ->
            val (height, source) = if (i < 20) {
                300.0 + i to ClimbSource.BAROMETER_STANDARD
            } else {
                450.0 + i to ClimbSource.BAROMETER
            }
            TripPoint(i * 1_000L, 48.1, 11.5, height + 5, 1f, 5f, height, source)
        }
        val live = points.fold(TripAccumulator(), TripAccumulator::add)
        val lines = listOf(TripCsv.HEADER) + points.map(TripCsv::encode)
        val reloaded = TripCsv.decodeFile(lines).fold(TripAccumulator(), TripAccumulator::add)
        assertEquals(live.stats, reloaded.stats)
        assertTrue(live.stats.ascentM < 40.0)
    }

    @Test
    fun `GPX is well formed with one trkpt per point`() {
        val t0 = Instant.parse("2026-09-28T19:12:03.250Z").toEpochMilli()
        val points = listOf(
            TripPoint(t0, 48.137, 11.575, 520.5, 1f, 4f),
            TripPoint(t0 + 1_000, 48.1371, 11.5751, null, 1f, 4f),
            TripPoint(t0 + 2_000, 48.1372, 11.5752, 521.0, null, null),
        )
        val out = StringBuilder()
        Gpx.write(points, "Morning <ride> & \"more\"", out)

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(out.toString().toByteArray()))
        val root = doc.documentElement
        assertEquals("gpx", root.localName)
        assertEquals("1.1", root.getAttribute("version"))
        assertEquals("Positioning Info", root.getAttribute("creator"))
        assertEquals("http://www.topografix.com/GPX/1/1", root.namespaceURI)
        assertEquals(1, doc.getElementsByTagName("trk").length)
        assertEquals(1, doc.getElementsByTagName("trkseg").length)
        assertEquals("Morning <ride> & \"more\"", doc.getElementsByTagName("name").item(0).textContent)

        val trkpts = doc.getElementsByTagName("trkpt")
        assertEquals(3, trkpts.length)
        assertEquals(2, doc.getElementsByTagName("ele").length)
        val first = trkpts.item(0) as Element
        assertEquals(48.137, first.getAttribute("lat").toDouble(), 1e-9)
        assertEquals(11.575, first.getAttribute("lon").toDouble(), 1e-9)
        assertEquals("2026-09-28T19:12:03.250Z", first.getElementsByTagName("time").item(0).textContent)
    }

    @Test
    fun `GPX of an empty trip is still valid`() {
        val out = StringBuilder()
        Gpx.write(emptyList(), "Empty", out)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(out.toString().toByteArray()))
        assertEquals(0, doc.getElementsByTagName("trkpt").length)
    }
}
