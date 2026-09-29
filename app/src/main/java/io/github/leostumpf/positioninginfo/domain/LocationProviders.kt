// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/** How precise a location provider says its fixes are. */
enum class ProviderAccuracy { FINE, COARSE }

/** How much power a location provider says it draws. */
enum class ProviderPower { LOW, MEDIUM, HIGH }

/**
 * What a location provider declares about itself (Android 12+): how good and how costly
 * its fixes are, what it depends on, and which quantities it can report.
 */
data class ProviderTraits(
    val accuracy: ProviderAccuracy?,
    val power: ProviderPower?,
    val needsSatellites: Boolean,
    val needsNetwork: Boolean,
    val needsCell: Boolean,
    val altitude: Boolean,
    val speed: Boolean,
    val bearing: Boolean,
    val monetaryCost: Boolean,
)

/** One location provider as the phone lists it; [traits] is null before Android 12. */
data class LocationProviderInfo(
    val name: String,
    val enabled: Boolean,
    val traits: ProviderTraits?,
) {
    /** What the provider is for, in words; vendor providers get a generic line. */
    val role: String
        get() = when (name) {
            "gps" -> "the GNSS receiver"
            "network" -> "position from Wi-Fi and cell towers"
            "fused" -> "Android's blend of all sources"
            "passive" -> "only relays fixes other apps asked for"
            else -> "added by the phone's maker"
        }

    /** "fine · high power", or null when the provider declares nothing. */
    val quality: String?
        get() = traits?.let { t ->
            listOfNotNull(
                t.accuracy?.let { if (it == ProviderAccuracy.FINE) "fine" else "coarse" },
                t.power?.let { "${it.name.lowercase()} power" },
                "may cost money".takeIf { t.monetaryCost },
            ).joinToString(" · ").ifEmpty { null }
        }

    /** "needs satellites · reports altitude, speed, bearing", or null. */
    val capabilities: String?
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

    companion object {
        private val ORDER = listOf("gps", "network", "fused", "passive")

        /** The platform providers in a fixed order, vendor ones after them by name. */
        fun sorted(providers: List<LocationProviderInfo>): List<LocationProviderInfo> =
            providers.sortedWith(
                compareBy<LocationProviderInfo> { ORDER.indexOf(it.name).let { i -> if (i < 0) ORDER.size else i } }
                    .thenBy { it.name },
            )
    }
}
