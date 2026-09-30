// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data.model

/**
 * A position from the platform network provider (Wi-Fi and cell towers, no satellites), or
 * from the fused provider, which reports through the same type.
 */
data class NetworkFix(
    val latitude: Double,
    val longitude: Double,
    /** 68 % confidence radius, as claimed by the provider. */
    val accuracyM: Float?,
    val elapsedRealtimeMs: Long,
    /**
     * "wifi" or "cell" when the provider says which it used. Google's provider puts this in
     * an undocumented extra, so it is absent on other devices.
     */
    val source: String?,
    val isCached: Boolean = false,
    val isMock: Boolean = false,
)

/** One cell the modem reports, serving or neighbour. */
data class CellTower(
    val technology: String,
    /** True for the cell the phone is attached to; the rest are neighbours it can hear. */
    val registered: Boolean,
    /** "MCC-MNC", e.g. "262-01", or null when not reported (usual for neighbours). */
    val network: String?,
    /** Tracking or location area, "TAC 1234", or null when not reported. */
    val area: String?,
    /** The cell's own number, "CI 5678", or null when not reported. */
    val cellId: String?,
    /** Physical cell ID / scrambling code: what distinguishes neighbours on the same channel. */
    val physicalId: Int?,
    /** What [physicalId] is called in this technology: PCI, BSIC or PSC. */
    val physicalIdLabel: String,
    val signalDbm: Int?,
    /**
     * Distance to the tower from the timing advance, or null when not reported. Only the
     * serving cell has one: the phone never transmits to its neighbours.
     */
    val timingAdvanceDistanceM: Double?,
    /** Operator name as broadcast by the cell, e.g. "Telefonica", when reported. */
    val operatorName: String? = null,
    /** Radio channel number: EARFCN, NR-ARFCN, ARFCN or UARFCN. */
    val channel: Int? = null,
    val channelLabel: String? = null,
    /** Frequency bands, e.g. [20] for LTE band 20 (Android 11+). */
    val bands: List<Int> = emptyList(),
    /** Technology-specific quality figures (RSRP, RSRQ, SINR, …), as reported. */
    val quality: List<SignalMeasure> = emptyList(),
    /** Android's 0–4 bars rating. */
    val level: Int? = null,
    val timingAdvanceSteps: Int? = null,
) {
    /** Area and cell together, "TAC 1234 · CI 5678", or null when neither is reported. */
    val identity: String? get() = listOfNotNull(area, cellId).joinToString(" · ").ifEmpty { null }
}

/** One reported signal figure, e.g. RSRP −95 dBm. */
data class SignalMeasure(val name: String, val value: Int, val unit: String)

/** One Wi-Fi access point from a scan; signal strength in dBm, frequency in MHz. */
data class AccessPoint(
    val ssid: String?,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channelWidthMhz: Int? = null,
    /** "Wi-Fi 6" etc. (Android 11+). */
    val standard: String? = null,
    /** "WPA3", "WPA2", "WEP", "open" — read from the advertised capabilities. */
    val security: String = "",
    /** Answers Wi-Fi round-trip-time ranging (IEEE 802.11mc), which can locate to about a metre. */
    val rttResponder: Boolean = false,
    /** How long ago this scan result was seen, in ms. */
    val ageMs: Long? = null,
)
