// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.data

import android.Manifest
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import io.github.leostumpf.gpstools.data.model.GnssSnapshot
import io.github.leostumpf.gpstools.data.model.SatelliteInfo
import io.github.leostumpf.gpstools.domain.Constellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Streams the full satellite picture from the platform GNSS status callback.
 *
 * The speedometer needs only "used / visible" from this; the GNSS status screen needs the
 * per-satellite constellation, signal strength and orbital-data flags. Both read the same
 * snapshot from this one source, so the two screens can never disagree about what the
 * receiver is seeing.
 */
class GnssStatusDataSource(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService<LocationManager>()

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun snapshots(): Flow<GnssSnapshot> = callbackFlow {
        val manager = locationManager
        if (manager == null) {
            close()
            return@callbackFlow
        }

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                trySend(status.toSnapshot())
            }

            override fun onStopped() {
                trySend(GnssSnapshot.EMPTY)
            }
        }

        // The Executor overload only exists from API 30; below that the Handler overload is
        // the only one available, so both paths are kept rather than raising minSdk.
        val registered = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.registerGnssStatusCallback(appContext.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            manager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
        }
        if (!registered) {
            trySend(GnssSnapshot.EMPTY)
        }

        awaitClose { manager.unregisterGnssStatusCallback(callback) }
    }
}

private fun GnssStatus.toSnapshot(): GnssSnapshot {
    val satellites = (0 until satelliteCount).map { i ->
        SatelliteInfo(
            svid = getSvid(i),
            constellation = Constellation.fromAndroidType(getConstellationType(i)),
            cn0DbHz = getCn0DbHz(i),
            elevationDegrees = getElevationDegrees(i),
            azimuthDegrees = getAzimuthDegrees(i),
            usedInFix = usedInFix(i),
            hasAlmanac = hasAlmanacData(i),
            hasEphemeris = hasEphemerisData(i),
            carrierFrequencyHz = if (hasCarrierFrequencyHz(i)) getCarrierFrequencyHz(i) else null,
        )
    }
    return GnssSnapshot(satellites = satellites, hasReported = true)
}
