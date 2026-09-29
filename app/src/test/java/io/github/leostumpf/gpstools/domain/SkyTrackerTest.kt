// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

import io.github.leostumpf.gpstools.data.model.SatelliteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SkyTrackerTest {

    private fun sat(
        svid: Int,
        az: Float,
        el: Float,
        freq: Float? = null,
        constellation: Constellation = Constellation.GPS,
    ) = SatelliteInfo(
        svid = svid,
        constellation = constellation,
        cn0DbHz = 30f,
        elevationDegrees = el,
        azimuthDegrees = az,
        usedInFix = true,
        hasAlmanac = true,
        hasEphemeris = true,
        carrierFrequencyHz = freq,
    )

    /** One sample per 10 s for [minutes], moving at the given rates in degrees per minute. */
    private fun samples(az0: Float, el0: Float, azRate: Float, elRate: Float, minutes: Int) =
        (0..minutes * 6).map { i ->
            val t = i / 6f
            SkySample(
                atMs = i * 10_000L,
                point = SkyPoint(((az0 + azRate * t) % 360f + 360f) % 360f, el0 + elRate * t),
            )
        }

    private fun azimuthStep(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    @Test
    fun `a satellite crossing north projects smoothly across 0 degrees`() {
        val history = samples(az0 = 355f, el0 = 40f, azRate = 1f, elRate = 0f, minutes = 4)
        val projection = SkyProjection.of(history, nowMs = history.last().atMs)
        assertNotNull(projection)
        val points = projection!!.points
        // No jump through south: consecutive points stay close together in azimuth.
        points.zipWithNext().forEach { (a, b) -> assertTrue(azimuthStep(a.azimuthDegrees, b.azimuthDegrees) < 5f) }
        assertTrue(points.last().azimuthDegrees in 5f..30f)
    }

    @Test
    fun `a descending satellite reports when it will set`() {
        // 10° up and dropping half a degree per minute: about 20 minutes left, outside the
        // window; at 5° it is about 10 minutes.
        val high = samples(az0 = 90f, el0 = 12f, azRate = 0f, elRate = -0.5f, minutes = 4)
        assertNull(SkyProjection.of(high, high.last().atMs)!!.setsInMinutes)

        val low = samples(az0 = 90f, el0 = 7f, azRate = 0f, elRate = -0.5f, minutes = 4)
        val setsIn = SkyProjection.of(low, low.last().atMs)!!.setsInMinutes
        assertNotNull(setsIn)
        assertEquals(10f, setsIn!!, 1.5f)
    }

    @Test
    fun `no projection without enough history`() {
        val short = samples(az0 = 90f, el0 = 40f, azRate = 1f, elRate = 0f, minutes = 1)
        assertNull(SkyProjection.of(short, short.last().atMs))
    }

    @Test
    fun `a stationary satellite gets no projection`() {
        val geo = samples(az0 = 160f, el0 = 30f, azRate = 0f, elRate = 0f, minutes = 5)
        assertNull(SkyProjection.of(geo, geo.last().atMs))
    }

    @Test
    fun `dual band signals collapse into one track`() {
        val tracker = SkyTracker().onSnapshot(
            listOf(sat(5, 100f, 50f, freq = 1.575e9f), sat(5, 100f, 50f, freq = 1.176e9f)),
            nowMs = 0L,
        )
        assertEquals(1, tracker.tracks.size)
    }

    @Test
    fun `unlocated satellites are ignored`() {
        val tracker = SkyTracker().onSnapshot(listOf(sat(5, 0f, 0f)), nowMs = 0L)
        assertTrue(tracker.tracks.isEmpty())
    }

    @Test
    fun `satellites found at start-up are not reported as appearing`() {
        val tracker = SkyTracker().onSnapshot(listOf(sat(5, 100f, 50f)), nowMs = 0L)
            .onSnapshot(listOf(sat(5, 100f, 50f), sat(9, 200f, 20f)), nowMs = 30_000L)
        assertTrue(tracker.events.isEmpty())
    }

    @Test
    fun `appearing and losing satellites produce events`() {
        var tracker = SkyTracker()
        for (t in 0L..120_000L step 1_000L) tracker = tracker.onSnapshot(listOf(sat(5, 100f, 50f)), t)
        tracker = tracker.onSnapshot(listOf(sat(5, 100f, 50f), sat(9, 200f, 8f)), 121_000L)
        assertEquals(SkyEvent.Kind.APPEARED, tracker.events.first().kind)
        assertEquals(9, tracker.events.first().id.svid)

        // Satellite 5 drops out; a minute later it is declared lost, stamped when last seen.
        for (t in 122_000L..190_000L step 1_000L) tracker = tracker.onSnapshot(listOf(sat(9, 200f, 8f)), t)
        val lost = tracker.events.first()
        assertEquals(SkyEvent.Kind.LOST, lost.kind)
        assertEquals(5, lost.id.svid)
        assertEquals(121_000L, lost.atMs)
    }

    @Test
    fun `backgrounding does not report every satellite as lost`() {
        var tracker = SkyTracker()
        for (t in 0L..120_000L step 1_000L) tracker = tracker.onSnapshot(listOf(sat(5, 100f, 50f)), t)
        tracker = tracker.onPause().onTick(600_000L).onSnapshot(listOf(sat(5, 110f, 45f)), 601_000L)
        assertTrue(tracker.events.isEmpty())
        assertTrue(tracker.tracks.getValue(SatelliteId(Constellation.GPS, 5)).visible)
    }

    @Test
    fun `history older than an hour is dropped`() {
        var tracker = SkyTracker()
        for (t in 0L..(SkyTracker.HISTORY_MS + 60_000L) step 10_000L) {
            tracker = tracker.onSnapshot(listOf(sat(5, 100f, 50f)), t)
        }
        val oldest = tracker.tracks.values.single().samples.first().atMs
        assertTrue(oldest >= 60_000L)
    }
}
