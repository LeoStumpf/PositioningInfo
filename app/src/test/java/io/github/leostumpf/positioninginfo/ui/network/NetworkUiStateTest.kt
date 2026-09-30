// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import io.github.leostumpf.positioninginfo.data.model.NetworkFix
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUiStateTest {

    private val now = 100_000L

    private fun gnss(
        ageMs: Long = 500,
        accuracy: Float? = 4f,
        cached: Boolean = false,
        lat: Double? = 48.137,
        lon: Double? = 11.575,
    ) = SpeedFix(
        speedMps = 0f,
        speedAccuracyMps = null,
        horizontalAccuracyM = accuracy,
        elapsedRealtimeMs = now - ageMs,
        isCached = cached,
        latitude = lat,
        longitude = lon,
    )

    private fun network(ageMs: Long = 1_000, lat: Double = 48.138, source: String? = "wifi") =
        NetworkFix(lat, 11.575, 25f, now - ageMs, source)

    private fun state(fix: NetworkFix?, gnss: SpeedFix?) = NetworkUiState.from(
        providerEnabled = true,
        fix = fix,
        gnss = gnss,
        nowMs = now,
        hasTelephony = true,
        cells = emptyList(),
        wifiAvailable = true,
        accessPoints = emptyList(),
    )

    @Test
    fun `a network fix against a current GNSS fix is compared`() {
        val s = state(network(), gnss())
        assertNotNull(s.comparison)
        assertEquals("", s.comparisonUnavailableReason)
        assertEquals("Wi-Fi", s.source)
        assertEquals(1_000L, s.ageMs)
    }

    @Test
    fun `each reason for no comparison is named`() {
        assertEquals("No network position yet.", state(null, gnss()).comparisonUnavailableReason)
        assertTrue(state(network(), null).comparisonUnavailableReason.startsWith("Needs a current GNSS fix"))
        assertTrue(
            state(network(), gnss(cached = true)).comparisonUnavailableReason.startsWith("Needs a current GNSS fix"),
        )
        assertTrue(state(network(), gnss(accuracy = 80f)).comparisonUnavailableReason.startsWith("GNSS is only ±80 m"))
    }

    @Test
    fun `position sources measure against GNSS, never against a cached fix`() {
        val rows = positionSources(
            gnss = gnss(),
            network = network(lat = 48.138),
            fused = null,
            gpsEnabled = true,
            networkEnabled = true,
            fusedEnabled = false,
            nowMs = now,
        )
        assertEquals(listOf("GNSS receiver", "Network", "Fused"), rows.map { it.name })
        assertTrue(rows[0].isReference)
        assertNull(rows[0].offsetM)
        // 0.001° of latitude is about 111 m.
        assertEquals(111.2, rows[1].offsetM!!, 0.5)
        assertEquals(1_000L, rows[1].ageMs)
        assertNull(rows[2].accuracyM)

        val cachedRows = positionSources(
            gnss = gnss(cached = true),
            network = network(),
            fused = null,
            gpsEnabled = true,
            networkEnabled = true,
            fusedEnabled = true,
            nowMs = now,
        )
        assertNull(cachedRows[0].accuracyM)
        assertNull(cachedRows[1].offsetM)
    }

    @Test
    fun `distances, bands and channels`() {
        assertEquals("35 m", formatDistance(35.7))
        assertEquals("1.2 km", formatDistance(1_234.0))
        assertEquals("2.4 GHz", band(2_437))
        assertEquals("5 GHz", band(5_180))
        assertEquals("6 GHz", band(5_975))
        assertEquals("900 MHz", band(900))
        assertEquals(6, wifiChannel(2_437))
        assertEquals(14, wifiChannel(2_484))
        assertEquals(36, wifiChannel(5_180))
        assertEquals(5, wifiChannel(5_975))
        assertNull(wifiChannel(3_000))
    }
}
