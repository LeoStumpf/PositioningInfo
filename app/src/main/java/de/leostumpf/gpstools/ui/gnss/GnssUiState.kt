// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.gnss

import de.leostumpf.gpstools.data.model.SatelliteInfo
import de.leostumpf.gpstools.domain.AlmanacReadiness
import de.leostumpf.gpstools.domain.AlmanacStatus
import de.leostumpf.gpstools.domain.ConstellationSummary
import de.leostumpf.gpstools.domain.band

/** Everything the GNSS status screen draws. */
data class GnssUiState(
    val readiness: AlmanacReadiness = AlmanacReadiness.UNKNOWN,
    val visible: Int = 0,
    val withAlmanac: Int = 0,
    val withEphemeris: Int = 0,
    val usedInFix: Int = 0,
    val perConstellation: List<ConstellationSummary> = emptyList(),
    /** One row per signal, so a satellite tracked on two bands appears twice. */
    val signals: List<SignalRow> = emptyList(),
    val gpsEnabled: Boolean = true,
    val ephemerisUnavailable: Boolean = false,
) {

    companion object {
        fun from(
            status: AlmanacStatus,
            satellites: List<SatelliteInfo>,
            gpsEnabled: Boolean,
        ) = GnssUiState(
            readiness = status.readiness,
            visible = status.visible,
            withAlmanac = status.withAlmanac,
            withEphemeris = status.withEphemeris,
            usedInFix = status.usedInFix,
            perConstellation = status.perConstellation,
            // Strongest signal first: the satellites actually doing the work sort to the top.
            signals = SignalRow.keyed(
                satellites.sortedWith(
                    compareByDescending<SatelliteInfo> { it.usedInFix }
                        .thenByDescending { it.cn0DbHz }
                        .thenBy { it.constellation.label }
                        .thenBy { it.svid },
                ),
            ),
            gpsEnabled = gpsEnabled,
            ephemerisUnavailable = status.ephemerisUnavailable,
        )
    }
}

/**
 * One signal in the satellite list, with a key that is unique within the list.
 *
 * Constellation and svid alone are not unique: a dual-frequency receiver reports the same
 * satellite once per band. The band disambiguates those, and an occurrence counter covers a
 * driver that repeats a satellite without reporting a carrier frequency — a duplicate key
 * would otherwise crash the list.
 */
data class SignalRow(val key: String, val satellite: SatelliteInfo) {
    companion object {
        fun keyed(signals: List<SatelliteInfo>): List<SignalRow> {
            val seen = mutableMapOf<String, Int>()
            return signals.map { sat ->
                val base = "${sat.constellation.name}-${sat.svid}-${sat.band?.name ?: "?"}"
                val occurrence = seen.merge(base, 1, Int::plus)!!
                SignalRow(key = if (occurrence == 1) base else "$base#$occurrence", satellite = sat)
            }
        }
    }
}
