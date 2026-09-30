// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui

import io.github.leostumpf.positioninginfo.data.CellInfoDataSource
import io.github.leostumpf.positioninginfo.data.LocationDataSource
import io.github.leostumpf.positioninginfo.data.LocationProviderDataSource
import io.github.leostumpf.positioninginfo.data.NetworkLocationDataSource
import io.github.leostumpf.positioninginfo.data.SystemStatusDataSource
import io.github.leostumpf.positioninginfo.data.WifiScanDataSource
import io.github.leostumpf.positioninginfo.data.model.PhoneSettings
import io.github.leostumpf.positioninginfo.domain.LocationProviderInfo
import io.github.leostumpf.positioninginfo.domain.PowerSaveLocation

/**
 * What the phone's settings and radios say around positioning, read on a schedule rather
 * than by every page's mapping: each read is a binder call on the main thread.
 *
 * [refreshGps] is cheap and runs on every tick and satellite sweep, since the location switch
 * changes what every page shows; [refreshSlow] covers what changes rarely, every few seconds.
 * Whether the phone has a modem or a given provider at all is read once.
 */
class PhoneFacts(
    private val location: LocationDataSource,
    private val network: NetworkLocationDataSource,
    private val fused: NetworkLocationDataSource,
    private val cells: CellInfoDataSource,
    private val wifi: WifiScanDataSource,
    private val system: SystemStatusDataSource,
    private val providerSource: LocationProviderDataSource,
) {
    var gpsEnabled = false
        private set
    var powerSave = PowerSaveLocation.UNRESTRICTED
        private set
    var airplaneMode = false
        private set
    var dataConnection: Boolean? = null
        private set
    var networkProviderEnabled = false
        private set
    var fusedProviderEnabled = false
        private set
    var wifiCanScan = false
        private set
    var phoneSettings = PhoneSettings()
        private set
    var providers = listOf<LocationProviderInfo>()
        private set
    var networkOffsetMs: Long? = null
        private set
    var systemGnssOffsetMs: Long? = null
        private set

    val hasTelephony by lazy { cells.hasTelephony }
    val networkProviderExists by lazy { network.exists }
    val fusedProviderExists by lazy { fused.exists }

    fun refreshGps() {
        gpsEnabled = location.isGpsEnabled
    }

    fun refreshSlow() {
        powerSave = system.powerSaveLocation
        airplaneMode = system.airplaneMode
        dataConnection = system.dataConnection
        networkProviderEnabled = networkProviderExists && network.isEnabled
        fusedProviderEnabled = fusedProviderExists && fused.isEnabled
        wifiCanScan = wifi.canScan
        phoneSettings = system.settings()
        providers = providerSource.read()
        networkOffsetMs = system.networkTimeOffsetMs()
        systemGnssOffsetMs = system.gnssTimeOffsetMs()
    }
}
