// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import io.github.leostumpf.positioninginfo.data.model.PhoneSettings
import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import io.github.leostumpf.positioninginfo.data.model.SignalMeasurement
import io.github.leostumpf.positioninginfo.domain.AcquisitionStage
import io.github.leostumpf.positioninginfo.domain.AlmanacReadiness
import io.github.leostumpf.positioninginfo.domain.AlmanacStatus
import io.github.leostumpf.positioninginfo.domain.ConstellationSummary
import io.github.leostumpf.positioninginfo.domain.Diagnosis
import io.github.leostumpf.positioninginfo.domain.HistorySample
import io.github.leostumpf.positioninginfo.domain.TtffEntry
import io.github.leostumpf.positioninginfo.domain.band

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
    /** Why there is (or is not) a fix; see [io.github.leostumpf.positioninginfo.domain.FixDiagnosis]. */
    val diagnosis: Diagnosis? = null,
    /** Raw-measurement detail per signal, keyed like [SignalRow.baseKey]. */
    val details: Map<String, SignalDetail> = emptyMap(),
    /** The last half hour, sampled every few seconds; in memory only. */
    val history: List<HistorySample> = emptyList(),
    /** Recent times to first fix, oldest first; stored on the phone. */
    val ttffLog: List<TtffEntry> = emptyList(),
    /** The phone settings around positioning. */
    val settings: PhoneSettings = PhoneSettings(),
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
    /**
     * Phone clock minus the time the network last gave it, and minus GNSS time as the
     * location system last learned it (either may predate this session). Null when unknown.
     */
    val networkOffsetMs: Long? = null,
    val systemGnssOffsetMs: Long? = null,
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
    /** The key without the duplicate counter: constellation, number and band. */
    val baseKey: String get() = key.substringBefore('#')

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

/** What the raw measurements add to one signal: how far acquisition got, and its Doppler. */
data class SignalDetail(
    /** Null when the chip reports no raw measurement for this signal. */
    val stage: AcquisitionStage?,
    /** True when the stage is deduced from the signal being in the fix, not reported. */
    val stageInferred: Boolean = false,
    val dopplerHz: Double?,
    val multipath: Boolean?,
    /** When the satellite was first heard this session, on the elapsed-realtime clock. */
    val firstHeardMs: Long?,
    /** The raw measurement itself, null when the chip reports none for this signal. */
    val raw: SignalMeasurement? = null,
)
