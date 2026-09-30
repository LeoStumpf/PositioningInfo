// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import io.github.leostumpf.positioninginfo.domain.LocationProviderInfo
import io.github.leostumpf.positioninginfo.domain.ProviderAccuracy
import io.github.leostumpf.positioninginfo.domain.ProviderPower
import io.github.leostumpf.positioninginfo.domain.ProviderTraits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderTextTest {

    private val gpsTraits = ProviderTraits(
        accuracy = ProviderAccuracy.FINE, power = ProviderPower.HIGH,
        needsSatellites = true, needsNetwork = false, needsCell = false,
        altitude = true, speed = true, bearing = true, monetaryCost = false,
    )

    @Test
    fun `declared traits read as two short lines`() {
        val gps = LocationProviderInfo("gps", enabled = true, traits = gpsTraits)
        assertEquals("fine · high power", gps.quality)
        assertEquals("needs satellites · reports altitude, speed, bearing", gps.capabilities)
    }

    @Test
    fun `a provider that declares nothing has no lines`() {
        val passive = LocationProviderInfo("passive", enabled = true, traits = null)
        assertNull(passive.quality)
        assertNull(passive.capabilities)
        val empty = LocationProviderInfo("x", true, gpsTraits.copy(
            accuracy = null, power = null, needsSatellites = false, altitude = false, speed = false, bearing = false,
        ))
        assertNull(empty.quality)
        assertNull(empty.capabilities)
    }

    @Test
    fun `vendor providers get a generic role`() {
        assertEquals("added by the phone's maker", LocationProviderInfo("qcom", true, null).role)
    }
}
