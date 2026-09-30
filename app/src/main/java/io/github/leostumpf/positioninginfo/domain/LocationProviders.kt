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
