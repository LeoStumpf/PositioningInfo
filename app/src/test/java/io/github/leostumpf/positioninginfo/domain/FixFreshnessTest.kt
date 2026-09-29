// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FixFreshnessTest {

    @Test
    fun `classifies fix age against the thresholds`() {
        assertEquals(FixFreshness.FRESH, FixFreshness.ofAge(0))
        assertEquals(FixFreshness.FRESH, FixFreshness.ofAge(2_999))
        assertEquals(FixFreshness.STALE, FixFreshness.ofAge(3_000))
        assertEquals(FixFreshness.STALE, FixFreshness.ofAge(9_999))
        assertEquals(FixFreshness.EXPIRED, FixFreshness.ofAge(10_000))
        assertEquals(FixFreshness.EXPIRED, FixFreshness.ofAge(600_000))
    }
}
