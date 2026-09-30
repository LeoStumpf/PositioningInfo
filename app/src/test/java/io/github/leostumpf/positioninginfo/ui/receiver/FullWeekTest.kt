// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullWeekTest {

    @Test
    fun `the 10-bit week resolves to the one nearest today`() {
        // Week 2380 (2025) broadcasts as 2380 mod 1024 = 332.
        assertEquals(2380, fullWeek(332, 2380))
        assertEquals(2380, fullWeek(332, 2385))
        // Just after a rollover: today is 3072 (broadcast 0), the message still says 1023.
        assertEquals(3071, fullWeek(1023, 3072))
        assertEquals(3072, fullWeek(0, 3071))
        assertNull(fullWeek(332, null))
    }
}
