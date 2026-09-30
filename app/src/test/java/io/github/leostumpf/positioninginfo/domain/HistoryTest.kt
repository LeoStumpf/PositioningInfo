// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    private fun sample(atMs: Long) = HistorySample(atMs, usedInFix = 8, heard = 12, meanCn0 = 30f, accuracyM = 4f)

    @Test
    fun `samples closer than the interval are skipped`() {
        val h = History().add(sample(0)).add(sample(1_000)).add(sample(5_000))
        assertEquals(listOf(0L, 5_000L), h.samples.map { it.atMs })
    }

    @Test
    fun `only the last half hour is kept`() {
        var h = History()
        for (t in 0L..History.WINDOW_MS + 60_000L step History.INTERVAL_MS) h = h.add(sample(t))
        val span = h.samples.last().atMs - h.samples.first().atMs
        assertEquals(History.WINDOW_MS, span)
    }

    @Test
    fun `time to first fix entries round-trip`() {
        val e = TtffEntry(1_790_000_000_000L, 4_700L, AlmanacReadiness.HOT)
        assertEquals(e, TtffEntry.decode(e.encode()))
        assertNull(TtffEntry.decode("garbage"))
        assertNull(TtffEntry.decode("1,2,NOPE"))
    }

    @Test
    fun `the first-fix log is read only under its own header`() {
        val entries = listOf(
            TtffEntry(1_000L, 4_700L, AlmanacReadiness.HOT),
            TtffEntry(2_000L, 31_000L, AlmanacReadiness.WARM),
        )
        assertEquals(entries, TtffEntry.decodeFile(TtffEntry.encodeFile(entries).lines()))
        assertTrue(TtffEntry.decodeFile(entries.map { it.encode() }).isEmpty())
    }
}
