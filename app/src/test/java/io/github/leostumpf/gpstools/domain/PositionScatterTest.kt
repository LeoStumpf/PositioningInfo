// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Test

class PositionScatterTest {

    private val baseLat = 48.137
    private val baseLon = 11.575
    private val metresPerDegLat = Math.toRadians(1.0) * 6_371_000.0

    private fun at(eastM: Double, northM: Double, alt: Double? = null, claimed: Float? = null) =
        PositionScatter.Sample(
            latitude = baseLat + northM / metresPerDegLat,
            longitude = baseLon + eastM / (metresPerDegLat * cos(Math.toRadians(baseLat))),
            altitudeM = alt,
            claimedAccuracyM = claimed,
        )

    private fun scatterOf(samples: List<PositionScatter.Sample>) =
        samples.fold(PositionScatter()) { s, x -> s.add(x) }

    @Test
    fun `no statistics below two samples`() {
        assertNull(PositionScatter().stats())
        assertNull(PositionScatter().add(at(0.0, 0.0)).stats())
    }

    @Test
    fun `identical fixes have no spread`() {
        val stats = scatterOf(List(10) { at(0.0, 0.0, alt = 520.0) }).stats()!!
        assertEquals(10, stats.count)
        assertEquals(baseLat, stats.meanLatitude, 1e-9)
        assertEquals(baseLon, stats.meanLongitude, 1e-9)
        assertEquals(0.0, stats.cep50M, 1e-6)
        assertEquals(0.0, stats.cep95M, 1e-6)
        assertEquals(0.0, stats.drmsM, 1e-6)
        assertEquals(0.0, stats.twoDrmsM, 1e-6)
        assertEquals(0.0, stats.altitudeStdDevM!!, 1e-9)
        assertNull(stats.meanClaimedAccuracyM)
        assertNull(stats.fractionWithinClaimed)
    }

    @Test
    fun `a ring of fixes at 10 m has all radii at 10 m`() {
        val ring = List(36) {
            val a = Math.toRadians(it * 10.0)
            at(10.0 * sin(a), 10.0 * cos(a))
        }
        val stats = scatterOf(ring).stats()!!
        assertEquals(baseLat, stats.meanLatitude, 1e-9)
        assertEquals(baseLon, stats.meanLongitude, 1e-9)
        assertEquals(10.0, stats.cep50M, 0.01)
        assertEquals(10.0, stats.cep95M, 0.01)
        assertEquals(10.0, stats.drmsM, 0.01)
        assertEquals(20.0, stats.twoDrmsM, 0.02)
        // Points are relative to the mean: the first one lies due north.
        assertEquals(0.0, stats.points[0].eastM, 0.01)
        assertEquals(10.0, stats.points[0].northM, 0.01)
    }

    @Test
    fun `percentiles interpolate between ranks`() {
        // Pairs at +k and -k north keep the mean at the base; distances are 1,1,2,2,...,10,10.
        val samples = (1..10).flatMap { k -> listOf(at(0.0, k.toDouble()), at(0.0, -k.toDouble())) }
        val stats = scatterOf(samples).stats()!!
        // 20 sorted distances: rank 0.5 * 19 = 9.5 lies between 5 and 6.
        assertEquals(5.5, stats.cep50M, 1e-3)
        assertEquals(10.0, stats.cep95M, 1e-3)
        assertEquals(sqrt(385.0 / 10), stats.drmsM, 1e-3)
    }

    @Test
    fun `fraction within claimed compares each fix to its own radius`() {
        val samples = listOf(
            at(10.0, 0.0, claimed = 5f),   // 10 m off, claims 5: outside
            at(-10.0, 0.0, claimed = 15f), // inside
            at(0.0, 10.0, claimed = 15f),  // inside
            at(0.0, -10.0, claimed = null),// not counted either way
        )
        val stats = scatterOf(samples).stats()!!
        assertEquals(35.0 / 3, stats.meanClaimedAccuracyM!!, 1e-9)
        assertEquals(2.0 / 3, stats.fractionWithinClaimed!!, 1e-9)
    }

    @Test
    fun `altitude spread uses only fixes with an altitude`() {
        val samples = listOf(at(0.0, 0.0, alt = 10.0), at(0.0, 0.0, alt = 14.0), at(0.0, 0.0, alt = null))
        val stats = scatterOf(samples).stats()!!
        assertEquals(sqrt(8.0), stats.altitudeStdDevM!!, 1e-9)
    }

    @Test
    fun `a cluster straddling the antimeridian averages to the right side`() {
        val samples = listOf(
            PositionScatter.Sample(0.0, 179.9999, null, null),
            PositionScatter.Sample(0.0, -179.9999, null, null),
        )
        val stats = scatterOf(samples).stats()!!
        assertEquals(180.0, kotlin.math.abs(stats.meanLongitude), 1e-6)
        assertEquals(11.1, stats.cep95M, 0.1)
    }

    @Test
    fun `keeps only the newest samples`() {
        var scatter = PositionScatter()
        repeat(PositionScatter.MAX_SAMPLES + 5) { scatter = scatter.add(at(it.toDouble(), 0.0)) }
        assertEquals(PositionScatter.MAX_SAMPLES, scatter.samples.size)
        assertEquals(at(5.0, 0.0), scatter.samples.first())
        assertEquals(at((PositionScatter.MAX_SAMPLES + 4).toDouble(), 0.0), scatter.samples.last())
    }
}
