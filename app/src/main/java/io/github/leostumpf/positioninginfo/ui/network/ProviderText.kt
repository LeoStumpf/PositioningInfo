// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import io.github.leostumpf.positioninginfo.domain.LocationProviderInfo
import io.github.leostumpf.positioninginfo.domain.ProviderAccuracy

/* A location provider's role and declared traits, in words, for the Wi-Fi & cell page. */

/** What the provider is for, in words; vendor providers get a generic line. */
val LocationProviderInfo.role: String
    get() = when (name) {
        "gps" -> "the GNSS receiver"
        "network" -> "position from Wi-Fi and cell towers"
        "fused" -> "Android's blend of all sources"
        "passive" -> "only relays fixes other apps asked for"
        else -> "added by the phone's maker"
    }

/** "fine · high power", or null when the provider declares nothing. */
val LocationProviderInfo.quality: String?
    get() = traits?.let { t ->
        listOfNotNull(
            t.accuracy?.let { if (it == ProviderAccuracy.FINE) "fine" else "coarse" },
            t.power?.let { "${it.name.lowercase()} power" },
            "may cost money".takeIf { t.monetaryCost },
        ).joinToString(" · ").ifEmpty { null }
    }

/** "needs satellites · reports altitude, speed, bearing", or null. */
val LocationProviderInfo.capabilities: String?
    get() = traits?.let { t ->
        val needs = listOfNotNull(
            "satellites".takeIf { t.needsSatellites },
            "network".takeIf { t.needsNetwork },
            "cell".takeIf { t.needsCell },
        )
        val reports = listOfNotNull(
            "altitude".takeIf { t.altitude },
            "speed".takeIf { t.speed },
            "bearing".takeIf { t.bearing },
        )
        listOfNotNull(
            needs.takeIf { it.isNotEmpty() }?.let { "needs ${it.joinToString(", ")}" },
            reports.takeIf { it.isNotEmpty() }?.let { "reports ${it.joinToString(", ")}" },
        ).joinToString(" · ").ifEmpty { null }
    }
