// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
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
    channelWidthMhz = when (channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> 20
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
        5 -> 320 // CHANNEL_WIDTH_320MHZ, Android 13
        else -> null
    },
    standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        when (wifiStandard) {
            4 -> "Wi-Fi 4 (802.11n)"
            5 -> "Wi-Fi 5 (802.11ac)"
            6 -> "Wi-Fi 6 (802.11ax)"
            7 -> "WiGig (802.11ad)"
            8 -> "Wi-Fi 7 (802.11be)"
            1 -> "802.11a/b/g"
            else -> null
        }
    } else {
        null
    },
    security = capabilities.orEmpty().let { c ->
        when {
            "SAE" in c || "WPA3" in c -> "WPA3"
            "WPA2" in c || "RSN" in c -> "WPA2"
            "WPA" in c -> "WPA"
            "WEP" in c -> "WEP"
            "OWE" in c -> "Enhanced open (OWE)"
            else -> "open"
        }
    },
    rttResponder = is80211mcResponder,
    ageMs = (android.os.SystemClock.elapsedRealtime() - timestamp / 1_000L).takeIf { timestamp > 0 && it >= 0 },
)
