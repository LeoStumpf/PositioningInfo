// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassTrustTest {

    @Test
    fun `a field matching the model is reliable`() {
        assertEquals(CompassTrust.Level.RELIABLE, CompassTrust(49.0, 48.0).level)
    }

    @Test
    fun `a moderately off field is suspect`() {
        assertEquals(CompassTrust.Level.SUSPECT, CompassTrust(56.0, 48.0).level)
        assertEquals(CompassTrust.Level.SUSPECT, CompassTrust(40.0, 48.0).level)
    }

    @Test
    fun `a magnet nearby is disturbed`() {
        assertEquals(CompassTrust.Level.DISTURBED, CompassTrust(150.0, 48.0).level)
    }

    @Test
    fun `magnitude combines the axes`() {
        assertEquals(50.0, CompassTrust.magnitude(30f, 40f, 0f), 1e-9)
    }
}
