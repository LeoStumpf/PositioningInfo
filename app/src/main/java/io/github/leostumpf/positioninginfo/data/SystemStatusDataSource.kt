// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.data.model.PhoneSettings
import io.github.leostumpf.positioninginfo.domain.PowerSaveLocation
import java.util.TimeZone

/**
 * The phone settings that decide whether the receiver can work well: battery saver's
 * location policy, airplane mode, and whether a data connection exists for assistance.
 *
 * Only reads state. Checking for a data connection needs ACCESS_NETWORK_STATE, which tells
 * whether a network is there — the app still has no permission to use it.
 */
class SystemStatusDataSource(context: Context) {

    private val appContext = context.applicationContext
    private val power = appContext.getSystemService<PowerManager>()
    private val connectivity = appContext.getSystemService<ConnectivityManager>()
    private val location = appContext.getSystemService<LocationManager>()
    private val wifi = appContext.getSystemService<WifiManager>()

    val powerSaveLocation: PowerSaveLocation
        get() {
            val manager = power ?: return PowerSaveLocation.UNRESTRICTED
            // Battery saver's effect on location is readable from Android 9.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !manager.isPowerSaveMode) {
                return PowerSaveLocation.UNRESTRICTED
            }
            return when (manager.locationPowerSaveMode) {
                PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF -> PowerSaveLocation.GNSS_OFF_SCREEN_OFF
                PowerManager.LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF -> PowerSaveLocation.ALL_OFF_SCREEN_OFF
                PowerManager.LOCATION_MODE_FOREGROUND_ONLY -> PowerSaveLocation.FOREGROUND_ONLY
                PowerManager.LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF -> PowerSaveLocation.THROTTLED_SCREEN_OFF
                else -> PowerSaveLocation.UNRESTRICTED
            }
        }

    val airplaneMode: Boolean
        get() = runCatching {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        }.getOrDefault(false)

    /** True with a working internet route (for the system's A-GNSS download), null if unknown. */
    val dataConnection: Boolean?
        get() = runCatching {
            val manager = connectivity ?: return null
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrNull()

    /** Everything around positioning a user can switch; see [PhoneSettings]. */
    fun settings(): PhoneSettings = PhoneSettings(
        preciseLocation = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED,
        locationOn = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) location?.isLocationEnabled else null
        }.getOrNull(),
        networkLocation = runCatching { location?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
        wifiScanning = runCatching {
            @Suppress("DEPRECATION")
            wifi?.isScanAlwaysAvailable
        }.getOrNull(),
        // Not a public constant, but a readable global setting on every Android version.
        bluetoothScanning = globalFlag("ble_scan_always_enabled"),
        autoTime = globalFlag(Settings.Global.AUTO_TIME),
        autoTimeZone = globalFlag(Settings.Global.AUTO_TIME_ZONE),
        timeZone = TimeZone.getDefault().id,
        powerSave = powerSaveLocation,
    )

    private fun globalFlag(name: String): Boolean? = runCatching {
        Settings.Global.getInt(appContext.contentResolver, name) == 1
    }.getOrNull()

    /**
     * The phone's clock minus the time the mobile network or a time server last gave it
     * (Android 13+), extrapolated to now. Null when the phone has no network time yet.
     */
    fun networkTimeOffsetMs(): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching { System.currentTimeMillis() - SystemClock.currentNetworkTimeClock().millis() }.getOrNull()
    }

    /**
     * The phone's clock minus GNSS time as the location system last learned it, extrapolated
     * to now. Available even before this app's first fix, if any app got one earlier.
     */
    fun gnssTimeOffsetMs(): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { System.currentTimeMillis() - SystemClock.currentGnssTimeClock().millis() }.getOrNull()
    }
}
