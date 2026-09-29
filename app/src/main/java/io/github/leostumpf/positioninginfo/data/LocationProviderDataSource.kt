// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.domain.LocationProviderInfo
import io.github.leostumpf.positioninginfo.domain.ProviderAccuracy
import io.github.leostumpf.positioninginfo.domain.ProviderPower
import io.github.leostumpf.positioninginfo.domain.ProviderTraits

/**
 * Every location provider the phone lists — the platform's gps, network, fused and
 * passive, plus any the maker added — with what each declares about itself.
 *
 * The declarations ([ProviderProperties]) are public from Android 12; before that only the
 * names and whether each is switched on are known.
 */
class LocationProviderDataSource(context: Context) {

    private val locationManager = context.applicationContext.getSystemService<LocationManager>()

    fun read(): List<LocationProviderInfo> = runCatching {
        val manager = locationManager ?: return emptyList()
        LocationProviderInfo.sorted(
            manager.allProviders.map { name ->
                LocationProviderInfo(
                    name = name,
                    enabled = runCatching { manager.isProviderEnabled(name) }.getOrDefault(false),
                    traits = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching { manager.getProviderProperties(name) }.getOrNull()?.toTraits()
                    } else {
                        null
                    },
                )
            },
        )
    }.getOrDefault(emptyList())
}

private fun ProviderProperties.toTraits() = ProviderTraits(
    accuracy = when (accuracy) {
        ProviderProperties.ACCURACY_FINE -> ProviderAccuracy.FINE
        ProviderProperties.ACCURACY_COARSE -> ProviderAccuracy.COARSE
        else -> null
    },
    power = when (powerUsage) {
        ProviderProperties.POWER_USAGE_LOW -> ProviderPower.LOW
        ProviderProperties.POWER_USAGE_MEDIUM -> ProviderPower.MEDIUM
        ProviderProperties.POWER_USAGE_HIGH -> ProviderPower.HIGH
        else -> null
    },
    needsSatellites = hasSatelliteRequirement(),
    needsNetwork = hasNetworkRequirement(),
    needsCell = hasCellRequirement(),
    altitude = hasAltitudeSupport(),
    speed = hasSpeedSupport(),
    bearing = hasBearingSupport(),
    monetaryCost = hasMonetaryCost(),
)
