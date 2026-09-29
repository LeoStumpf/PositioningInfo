// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedFilterTest {

    @Test
    fun `noise below the receiver's own accuracy reads as standing still`() {
        assertEquals(0f, SpeedFilter.apply(speedMps = 0.6f, speedAccuracyMps = 0.9f), 0f)
    }

    @Test
    fun `motion above the accuracy figure passes through untouched`() {
        assertEquals(12.5f, SpeedFilter.apply(speedMps = 12.5f, speedAccuracyMps = 0.4f), 0f)
    }

    @Test
    fun `missing accuracy falls back to the fixed noise floor`() {
        assertEquals(0f, SpeedFilter.apply(speedMps = 0.3f, speedAccuracyMps = null), 0f)
        assertEquals(0.8f, SpeedFilter.apply(speedMps = 0.8f, speedAccuracyMps = null), 0f)
    }

    @Test
    fun `an implausible accuracy figure cannot swallow real motion`() {
        // A receiver reporting 50 m/s of uncertainty must not turn a genuine 5 m/s into zero.
        assertEquals(5f, SpeedFilter.apply(speedMps = 5f, speedAccuracyMps = 50f), 0f)
    }

    @Test
    fun `an implausibly optimistic accuracy still applies the minimum floor`() {
        // Some receivers report near-zero accuracy while stationary; the floor must hold.
        assertEquals(0f, SpeedFilter.apply(speedMps = 0.2f, speedAccuracyMps = 0.01f), 0f)
    }
}
