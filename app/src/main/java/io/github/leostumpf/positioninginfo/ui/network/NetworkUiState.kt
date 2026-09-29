// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import io.github.leostumpf.positioninginfo.data.model.AccessPoint
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.data.model.NetworkFix
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.domain.NetworkComparison

/** Everything the network-location page draws. */
data class NetworkUiState(
    val providerEnabled: Boolean = true,
    val accuracyM: Float? = null,
    /** "Wi-Fi" or "Cell" when the provider says which it used. */
    val source: String? = null,
    val ageMs: Long? = null,
    val comparison: NetworkComparison? = null,
    /** Why there is no comparison, shown in its place. */
    val comparisonUnavailableReason: String = "",
    val hasTelephony: Boolean = true,
    val cells: List<CellTower> = emptyList(),
    val wifiAvailable: Boolean = true,
    val accessPoints: List<AccessPoint> = emptyList(),
    /** GNSS, network and fused positions side by side. */
    val sources: List<SourceRow> = emptyList(),
) {
    companion object {
        fun from(
            providerEnabled: Boolean,
            fix: NetworkFix?,
            gnss: SpeedFix?,
            nowMs: Long,
            hasTelephony: Boolean,
            cells: List<CellTower>,
            wifiAvailable: Boolean,
            accessPoints: List<AccessPoint>,
        ): NetworkUiState {
            val comparison = NetworkComparison.of(fix, gnss, nowMs)
            return NetworkUiState(
                providerEnabled = providerEnabled,
                accuracyM = fix?.accuracyM,
                source = when (fix?.source) {
                    "wifi" -> "Wi-Fi"
                    "cell" -> "Cell"
                    null -> null
                    else -> fix.source
                },
                ageMs = fix?.let { (nowMs - it.elapsedRealtimeMs).coerceAtLeast(0L) },
                comparison = comparison,
                comparisonUnavailableReason = when {
                    comparison != null -> ""
                    fix == null -> "No network position yet."
                    gnss?.latitude == null ||
                        gnss.isCached ||
                        nowMs - gnss.elapsedRealtimeMs > NetworkComparison.MAX_GNSS_AGE_MS ->
                        "Needs a current GNSS fix as the reference — go outside."
                    (gnss.horizontalAccuracyM ?: Float.MAX_VALUE) > NetworkComparison.MAX_GNSS_ACCURACY_M ->
                        "GNSS is only ±${gnss.horizontalAccuracyM?.toInt()} m right now, too coarse " +
                            "to judge the network position against."
                    else -> "The network position is too old to compare."
                },
                hasTelephony = hasTelephony,
                cells = cells,
                wifiAvailable = wifiAvailable,
                accessPoints = accessPoints,
            )
        }
    }
}

/** "±35 m" or "±1.2 km". */
internal fun formatDistance(metres: Double): String =
    if (metres < 1_000) "${metres.toInt()} m" else String.format(java.util.Locale.US, "%.1f km", metres / 1_000)

internal fun formatAge(ms: Long): String = when {
    ms < 2_000 -> "just now"
    ms < 60_000 -> "${ms / 1_000} s ago"
    else -> "${ms / 60_000} min ago"
}

internal fun band(frequencyMhz: Int): String = when (frequencyMhz) {
    in 2_400..2_500 -> "2.4 GHz"
    in 4_900..5_900 -> "5 GHz"
    in 5_925..7_125 -> "6 GHz"
    else -> "$frequencyMhz MHz"
}

/** One position source in the comparison. */
data class SourceRow(
    val name: String,
    val description: String,
    val available: Boolean,
    val accuracyM: Float?,
    val ageMs: Long?,
    /** Distance to the GNSS position, or null for GNSS itself or when either is missing. */
    val offsetM: Double?,
    val isReference: Boolean = false,
    val isMock: Boolean = false,
)
