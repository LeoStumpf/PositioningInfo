// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedHistoryTest {

    @Test
    fun `one sample per interval, keeping the faster`() {
        val h = SpeedHistory().add(0, 5f).add(300, 9f).add(600, 7f).add(1_000, 3f)
        assertEquals(listOf(9f, 3f), h.samples.map { it.mps })
    }

    @Test
    fun `a long session is thinned but keeps its peak`() {
        var h = SpeedHistory()
        for (i in 0 until 2_000) h = h.add(i * 1_000L, if (i == 1_234) 42f else 10f)
        assertTrue(h.samples.size <= SpeedHistory.MAX_SAMPLES)
        assertEquals(42f, h.maxMps)
        assertEquals(0L, h.startedAtMs)
        assertTrue(h.intervalMs > SpeedHistory.INTERVAL_MS)
    }
}
