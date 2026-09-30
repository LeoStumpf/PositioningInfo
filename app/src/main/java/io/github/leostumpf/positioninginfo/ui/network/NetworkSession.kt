// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import android.annotation.SuppressLint
import android.os.SystemClock
import io.github.leostumpf.positioninginfo.data.CellInfoDataSource
import io.github.leostumpf.positioninginfo.data.NetworkLocationDataSource
import io.github.leostumpf.positioninginfo.data.WifiScanDataSource
import io.github.leostumpf.positioninginfo.data.model.AccessPoint
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.data.model.NetworkFix
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.ui.PhoneFacts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Positioning without satellites, for the network page: the network and fused providers'
 * positions, the cells and the Wi-Fi access points they are built from, all compared with
 * the GNSS fix.
 *
 * @param gnssFix the latest GNSS fix, the reference every other source is measured against.
 * @param visible whether the app is on screen; out of sight the page is not rebuilt.
 */
internal class NetworkSession(
    private val scope: CoroutineScope,
    private val network: NetworkLocationDataSource,
    private val fused: NetworkLocationDataSource,
    private val cellSource: CellInfoDataSource,
    private val wifi: WifiScanDataSource,
    private val facts: PhoneFacts,
    private val gnssFix: () -> SpeedFix?,
    private val visible: () -> Boolean,
) {
    private val _state = MutableStateFlow(NetworkUiState())
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    private var networkFix: NetworkFix? = null
    private var fusedFix: NetworkFix? = null
    private var cells: List<CellTower> = emptyList()
    private var accessPoints: List<AccessPoint> = emptyList()

    /**
     * Starts every source this phone has; the returned jobs are cancelled by the owner. All
     * are cheap: the network provider does one lookup every few seconds, and the cell and
     * Wi-Fi readings mostly return what the radios already know.
     */
    @SuppressLint("MissingPermission") // only called by startTracking, after its permission check
    fun start(): List<Job> = buildList {
        add(follow(network.fixes()) { networkFix = it })
        if (facts.fusedProviderExists) add(follow(fused.fixes()) { fusedFix = it })
        if (facts.hasTelephony) add(follow(cellSource.cells()) { cells = it })
        add(follow(wifi.accessPoints()) { accessPoints = it })
    }

    /** Keeps each new value of [flow] and rebuilds the page with it. */
    private fun <T> follow(flow: Flow<T>, keep: (T) -> Unit): Job = scope.launch {
        flow.collect {
            keep(it)
            publish()
        }
    }

    fun publish() {
        if (!visible()) return
        val gnss = gnssFix()
        val now = SystemClock.elapsedRealtime()
        _state.value = NetworkUiState.from(
            providerEnabled = facts.networkProviderEnabled,
            fix = networkFix,
            gnss = gnss,
            nowMs = now,
            hasTelephony = facts.hasTelephony,
            cells = cells,
            wifiAvailable = facts.wifiCanScan,
            accessPoints = accessPoints,
        ).copy(
            sources = positionSources(
                gnss = gnss,
                network = networkFix,
                fused = fusedFix,
                gpsEnabled = facts.gpsEnabled,
                networkEnabled = facts.networkProviderEnabled,
                fusedEnabled = facts.fusedProviderEnabled,
                nowMs = now,
            ),
            providers = facts.providers,
        )
    }
}
