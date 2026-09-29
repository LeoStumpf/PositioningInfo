// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.data.model.AccessPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Lists the Wi-Fi access points in range: the inputs of Wi-Fi positioning.
 *
 * Android throttles scans requested by apps to four per two minutes, so this asks rarely
 * and mostly relies on the scans the system runs on its own while the screen is on.
 */
class WifiScanDataSource(context: Context) {

    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService<WifiManager>()

    /** Scanning works with Wi-Fi off too, if "Wi-Fi scanning" is enabled in location settings. */
    val canScan: Boolean
        get() = runCatching {
            wifi?.let { it.isWifiEnabled || it.isScanAlwaysAvailable } == true
        }.getOrDefault(false)

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun accessPoints(): Flow<List<AccessPoint>> = callbackFlow {
        val manager = wifi
        if (manager == null) {
            close()
            return@callbackFlow
        }
        @Suppress("MissingPermission")
        fun read() = runCatching { manager.scanResults }.getOrNull().orEmpty()
            .map { it.toAccessPoint() }
            .sortedByDescending { it.rssiDbm }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read())
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(read())

        // A scan request now and then, well inside the throttle, plus a re-read in case a
        // broadcast was missed.
        val poller = launch {
            var sinceScanMs = SCAN_EVERY_MS
            while (true) {
                if (sinceScanMs >= SCAN_EVERY_MS) {
                    @Suppress("DEPRECATION")
                    runCatching { manager.startScan() }
                    sinceScanMs = 0L
                }
                delay(REREAD_EVERY_MS)
                sinceScanMs += REREAD_EVERY_MS
                trySend(read())
            }
        }

        awaitClose {
            poller.cancel()
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    private companion object {
        const val SCAN_EVERY_MS = 40_000L
        const val REREAD_EVERY_MS = 10_000L
    }
}

@Suppress("DEPRECATION")  // wifiSsid replaces SSID only from API 33
private fun ScanResult.toAccessPoint() = AccessPoint(
    ssid = SSID?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" },
    bssid = BSSID.orEmpty(),
    rssiDbm = level,
    frequencyMhz = frequency,
)
