// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data.model

/** A position from the platform network provider: Wi-Fi and cell towers, no satellites. */
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
)

/** One cell the modem reports, serving or neighbour. */
data class CellTower(
    val technology: String,
    /** True for the cell the phone is attached to; the rest are neighbours it can hear. */
    val registered: Boolean,
    /** "MCC-MNC", e.g. "262-01", or null when not reported (usual for neighbours). */
    val network: String?,
    /** Area code and cell identity, formatted for display, or null when not reported. */
    val identity: String?,
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
)

data class AccessPoint(
    val ssid: String?,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
)
