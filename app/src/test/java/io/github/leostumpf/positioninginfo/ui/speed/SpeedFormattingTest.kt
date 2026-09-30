// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.speed

import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedFormattingTest {

    @Test
    fun `one decimal below 100, whole numbers above`() {
        assertEquals("36.0", formatSpeed(10.0, SpeedUnit.KMH))
        assertEquals("99.9", formatSpeed(27.75, SpeedUnit.KMH))
        assertEquals("108", formatSpeed(30.0, SpeedUnit.KMH))
        assertEquals("22.4", formatSpeed(10.0, SpeedUnit.entries.single { it.symbol == "mph" }))
    }

    @Test
    fun `no value is shown as a dash, not zero`() {
        assertEquals(NO_VALUE, formatSpeed(null as Double?, SpeedUnit.KMH))
        assertEquals(NO_VALUE, formatAccuracy(null))
        assertEquals("±3.5 m", formatAccuracy(3.5f))
    }
}
