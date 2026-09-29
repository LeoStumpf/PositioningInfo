// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import io.github.leostumpf.gpstools.data.model.NetworkFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Streams positions from the platform network provider.
 *
 * Still the AOSP API, not Play Services: on a Pixel the platform delegates this provider to
 * Google's location service, which looks the nearby Wi-Fi access points and cell towers up
 * in its database. Whether it works at all depends on the "Google Location Accuracy" setting.
 */
class NetworkLocationDataSource(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService<LocationManager>()

    /** False when the device has no network provider or it is switched off in settings. */
    val isEnabled: Boolean
        get() = runCatching {
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        }.getOrDefault(false)

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun fixes(): Flow<NetworkFix> = callbackFlow {
        val manager = locationManager
        if (manager == null || LocationManager.NETWORK_PROVIDER !in manager.allProviders) {
            close()
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toNetworkFix())
            }

            @Deprecated("Retained for API levels below 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        manager.requestUpdates(listener)
        runCatching { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }
            .getOrNull()
            ?.let { trySend(it.toNetworkFix().copy(isCached = true)) }

        awaitClose { manager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    private fun LocationManager.requestUpdates(listener: LocationListener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                LocationRequest.Builder(UPDATE_INTERVAL_MS).setMinUpdateDistanceMeters(0f).build(),
                appContext.mainExecutor,
                listener,
            )
        } else {
            requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        }
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 5_000L
    }
}

private fun Location.toNetworkFix(): NetworkFix = NetworkFix(
    latitude = latitude,
    longitude = longitude,
    accuracyM = if (hasAccuracy()) accuracy else null,
    elapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000L,
    source = runCatching { extras?.getString("networkLocationType") }.getOrNull(),
)
