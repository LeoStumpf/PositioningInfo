// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationProvidersTest {

    @Test
    fun `platform providers come first in a fixed order, vendor ones after`() {
        val names = LocationProviderInfo.sorted(
            listOf("zeta", "passive", "fused", "alpha", "gps", "network").map { LocationProviderInfo(it, true, null) },
        ).map { it.name }
        assertEquals(listOf("gps", "network", "fused", "passive", "alpha", "zeta"), names)
    }
}
