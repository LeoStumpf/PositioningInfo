// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.data

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
import de.leostumpf.gpstools.data.model.SpeedFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Streams GNSS fixes from the platform [LocationManager].
 *
 * Deliberately uses the AOSP location APIs and the raw GPS provider rather than Play
 * Services' fused provider: the fused provider blends in network and sensor sources, is
 * proprietary, and cannot supply the GNSS status this app is built to grow into. The GPS
 * provider hands over the receiver's own Doppler-derived speed, which is exactly what a
 * speedometer wants.
 */
class LocationDataSource(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService<LocationManager>()

    /** False when the user has location switched off entirely, which the UI reports differently from "no fix yet". */
    val isGpsEnabled: Boolean
        get() = runCatching {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        }.getOrDefault(false)

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun fixes(): Flow<SpeedFix> = callbackFlow {
        val manager = locationManager
        if (manager == null) {
            close()
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toSpeedFix())
            }

            // Required on API < 30; the default implementations are final-ish no-ops there.
            @Deprecated("Retained for API levels below 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        manager.requestUpdates(listener)

        // Seed the stream with the last known fix so the screen has something to show
        // immediately; its age drives the freshness indicator, so a stale seed is honest.
        manager.lastKnownFixOrNull()?.let { trySend(it.copy(isCached = true)) }

        awaitClose { manager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    private fun LocationManager.requestUpdates(listener: LocationListener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LocationRequest.Builder(UPDATE_INTERVAL_MS)
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                    .setMinUpdateDistanceMeters(0f)
                    .build(),
                appContext.mainExecutor,
                listener,
            )
        } else {
            requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun LocationManager.lastKnownFixOrNull(): SpeedFix? =
        runCatching { getLastKnownLocation(LocationManager.GPS_PROVIDER) }
            .getOrNull()
            ?.toSpeedFix()

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}

private fun Location.toSpeedFix(): SpeedFix = SpeedFix(
    speedMps = if (hasSpeed()) speed else null,
    speedAccuracyMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    horizontalAccuracyM = if (hasAccuracy()) accuracy else null,
    elapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000L,
)
