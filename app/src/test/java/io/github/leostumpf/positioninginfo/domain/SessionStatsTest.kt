// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsTest {

    @Test
    fun `starts empty`() {
        val stats = SessionStats()
        assertNull(stats.averageMps)
        assertEquals(0.0, stats.maxMps, 0.0)
        assertTrue(!stats.hasData)
    }

    @Test
    fun `first sample carries no weight but establishes the maximum`() {
        // There is no interval to weight yet, so an average would be meaningless.
        val stats = SessionStats().accept(20.0, atElapsedMs = 1_000)
        assertEquals(20.0, stats.maxMps, 0.0)
        assertNull(stats.averageMps)
        assertTrue(stats.hasData)
    }

    @Test
    fun `average is time weighted, not a mean of samples`() {
        // 10 m/s held for 1 s, then 30 m/s held for 3 s.
        // Time weighted: (10*1 + 30*3) / 4 = 25 m/s.  A naive sample mean would say 20.
        val stats = SessionStats()
            .accept(0.0, atElapsedMs = 0)
            .accept(10.0, atElapsedMs = 1_000)
            .accept(30.0, atElapsedMs = 4_000)

        assertEquals(25.0, stats.averageMps!!, 0.0001)
    }

    @Test
    fun `a reception gap contributes no weight`() {
        // 20 m/s for 2 s, then a 60 s blackout, then a sample at 4 m/s.
        // The blackout must not be charged to either speed: only the 2 s interval counts,
        // so the average stays 20 rather than being dragged toward the post-gap value.
        val stats = SessionStats()
            .accept(20.0, atElapsedMs = 0)
            .accept(20.0, atElapsedMs = 2_000)
            .accept(4.0, atElapsedMs = 62_000)

        assertEquals(20.0, stats.averageMps!!, 0.0001)
        assertEquals(2_000L, stats.weightedTimeMs)
    }

    @Test
    fun `a sample after a gap still re-anchors the clock`() {
        val stats = SessionStats()
            .accept(20.0, atElapsedMs = 0)
            .accept(4.0, atElapsedMs = 60_000) // gap: no weight
            .accept(4.0, atElapsedMs = 61_000) // 1 s at 4 m/s: counts

        assertEquals(4.0, stats.averageMps!!, 0.0001)
        assertEquals(1_000L, stats.weightedTimeMs)
    }

    @Test
    fun `maximum survives a later slowdown`() {
        val stats = SessionStats()
            .accept(5.0, atElapsedMs = 0)
            .accept(33.0, atElapsedMs = 1_000)
            .accept(2.0, atElapsedMs = 2_000)

        assertEquals(33.0, stats.maxMps, 0.0)
    }

    @Test
    fun `non-monotonic timestamps are ignored rather than corrupting the average`() {
        val stats = SessionStats()
            .accept(10.0, atElapsedMs = 5_000)
            .accept(10.0, atElapsedMs = 4_000) // clock went backwards
            .accept(10.0, atElapsedMs = 5_000)

        assertEquals(1_000L, stats.weightedTimeMs)
        assertEquals(10.0, stats.averageMps!!, 0.0001)
    }

    @Test
    fun `accumulated distance matches the weighted time`() {
        val stats = SessionStats()
            .accept(0.0, atElapsedMs = 0)
            .accept(10.0, atElapsedMs = 2_000)

        assertEquals(20.0, stats.distanceM, 0.0001) // 10 m/s over 2 s
        assertEquals(10.0, stats.averageMps!!, 0.0001)
    }
}
