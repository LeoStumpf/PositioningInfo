// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralTest {

    @Test
    fun `one is singular, everything else plural`() {
        assertEquals("1 satellite", 1.counted("satellite"))
        assertEquals("0 satellites", 0.counted("satellite"))
        assertEquals("6 satellites", 6.counted("satellite"))
        assertEquals("1 entry", 1.counted("entry", "entries"))
        assertEquals("3 entries", 3.counted("entry", "entries"))
    }
}
