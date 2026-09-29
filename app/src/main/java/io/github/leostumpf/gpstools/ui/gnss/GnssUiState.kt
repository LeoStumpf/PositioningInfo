// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.ui.gnss

import io.github.leostumpf.gpstools.data.model.SatelliteInfo
import io.github.leostumpf.gpstools.domain.AlmanacReadiness
import io.github.leostumpf.gpstools.domain.AlmanacStatus
import io.github.leostumpf.gpstools.domain.ConstellationSummary
import io.github.leostumpf.gpstools.domain.band

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
    val timing: TimingUiState = TimingUiState(),
    /** Outcome of the last cold start or assistance download, or null if none was requested. */
    val assistanceMessage: String? = null,
) {

    companion object {
        fun from(
            status: AlmanacStatus,
            satellites: List<SatelliteInfo>,
            gpsEnabled: Boolean,
            timing: TimingUiState,
            assistanceMessage: String?,
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
            timing = timing,
            assistanceMessage = assistanceMessage,
        )
    }
}

/** How long this session's first fix took, and how the phone's clock compares with GNSS time. */
data class TimingUiState(
    /** Time to first fix, or null while still searching. */
    val firstFixMs: Long? = null,
    /** How long the receiver has been searching, or null once it has a fix. */
    val searchingForMs: Long? = null,
    /** Phone clock minus GNSS time; positive when the phone is ahead. Null before a fix. */
    val clockOffsetMs: Long? = null,
)

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
