// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.domain.PowerSaveLocation

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

    val powerSaveLocation: PowerSaveLocation
        get() {
            val manager = power ?: return PowerSaveLocation.UNRESTRICTED
            if (!manager.isPowerSaveMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return PowerSaveLocation.UNRESTRICTED
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
}
