// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationsTest {

    @Test
    fun `tenths only below ten seconds, then seconds, minutes and hours`() {
        assertEquals("4.2 s", formatDuration(4_249L))
        assertEquals("37 s", formatDuration(37_900L))
        assertEquals("1 min", formatDuration(60_000L))
        assertEquals("12 min 5 s", formatDuration(725_000L))
        assertEquals("2 h", formatDuration(7_200_000L))
        assertEquals("2 h 5 min", formatDuration(7_530_000L))
        assertEquals("0.0 s", formatDuration(-5L))
    }

    @Test
    fun `ago is coarse`() {
        assertEquals("just now", formatAgo(1_500L))
        assertEquals("37 s ago", formatAgo(37_000L))
        assertEquals("12 min ago", formatAgo(725_000L))
        assertEquals("2 h 5 min ago", formatAgo(7_530_000L))
    }
}
