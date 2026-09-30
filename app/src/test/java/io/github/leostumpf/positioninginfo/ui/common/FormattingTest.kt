// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattingTest {

    @Test
    fun `numbers are written the same whatever the phone's language`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("12.35", 12.345.fmt(2))
            assertEquals("12 345", 12_345.grouped())
            assertEquals("1 234 567", 1_234_567L.grouped())
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `metres switch to kilometres, durations to hours`() {
        assertEquals("12.3 m", metres(12.34))
        assertEquals("1.23 km", metres(1_234.0))
        assertEquals("-1.50 km", metres(-1_500.0))
        assertEquals(DASH, metres(null))
        assertEquals("4:05", duration(245_000))
        assertEquals("1:02:03", duration(3_723_000))
    }
}
