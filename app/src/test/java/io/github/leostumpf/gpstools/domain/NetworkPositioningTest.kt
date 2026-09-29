// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

import io.github.leostumpf.gpstools.data.model.NetworkFix
import io.github.leostumpf.gpstools.data.model.SpeedFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPositioningTest {

    private fun gnss(atMs: Long = 0L, accuracy: Float = 4f, lat: Double = 48.0, cached: Boolean = false) = SpeedFix(
        speedMps = 0f,
        speedAccuracyMps = null,
        horizontalAccuracyM = accuracy,
        elapsedRealtimeMs = atMs,
        isCached = cached,
        latitude = lat,
        longitude = 11.0,
    )

    private fun network(atMs: Long = 0L, accuracy: Float? = 40f, lat: Double = 48.0) =
        NetworkFix(lat, 11.0, accuracy, atMs, source = "wifi")

    @Test
    fun `one thousandth of a degree of latitude is about 111 m`() {
        assertEquals(111.2, NetworkComparison.distanceM(48.0, 11.0, 48.001, 11.0), 0.5)
    }

    @Test
    fun `the distance to a good GNSS fix is the actual error`() {
        val c = NetworkComparison.of(network(lat = 48.0003), gnss(), nowMs = 1_000L)
        assertNotNull(c)
        assertEquals(33.4, c!!.distanceM, 0.5)
        assertTrue(c.withinClaimed!!)
    }

    @Test
    fun `an error beyond the claimed radius is flagged`() {
        val c = NetworkComparison.of(network(lat = 48.001, accuracy = 40f), gnss(), nowMs = 1_000L)
        assertEquals(false, c!!.withinClaimed)
    }

    @Test
    fun `no comparison against a coarse, stale or cached GNSS fix`() {
        assertNull(NetworkComparison.of(network(), gnss(accuracy = 60f), nowMs = 1_000L))
        assertNull(NetworkComparison.of(network(atMs = 20_000L), gnss(atMs = 0L), nowMs = 20_000L))
        assertNull(NetworkComparison.of(network(), gnss(cached = true), nowMs = 1_000L))
    }

    @Test
    fun `timing advance converts to distance`() {
        assertEquals(546.8, TimingAdvance.lteMetres(7)!!, 0.1)
        assertEquals(1_107.0, TimingAdvance.gsmMetres(2)!!, 0.1)
        assertEquals(149.9, TimingAdvance.nrMetres(1)!!, 0.1)
        assertNull(TimingAdvance.lteMetres(Int.MAX_VALUE))
    }
}
