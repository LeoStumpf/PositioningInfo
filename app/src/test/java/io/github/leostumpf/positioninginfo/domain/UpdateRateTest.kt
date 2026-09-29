// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateRateTest {

    @Test
    fun `one fix gives no rate`() {
        assertNull(UpdateRate().onFix(1_000L).hz)
    }

    @Test
    fun `regular fixes give their rate`() {
        var r = UpdateRate()
        for (t in 0L..10_000L step 1_000L) r = r.onFix(t)
        assertEquals(1_000L, r.meanIntervalMs)
        assertEquals(1.0, r.hz!!, 1e-9)
    }

    @Test
    fun `only the recent window counts`() {
        var r = UpdateRate()
        for (t in 0L..5_000L step 1_000L) r = r.onFix(t)
        for (t in 7_000L..40_000L step 2_000L) r = r.onFix(t)
        assertEquals(2_000L, r.meanIntervalMs)
    }

    @Test
    fun `a long gap restarts the measurement`() {
        var r = UpdateRate().onFix(0L).onFix(1_000L)
        r = r.onFix(60_000L)
        assertNull(r.meanIntervalMs)
    }

    @Test
    fun `duplicate timestamps are ignored`() {
        val r = UpdateRate().onFix(1_000L).onFix(1_000L).onFix(2_000L)
        assertEquals(1_000L, r.meanIntervalMs)
    }
}
