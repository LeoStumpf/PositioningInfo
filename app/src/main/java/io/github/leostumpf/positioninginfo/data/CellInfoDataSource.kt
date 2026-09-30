// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.data.model.CellTower
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Polls the modem for the cells it can hear.
 *
 * These are the raw inputs of cell-based positioning: which towers are in range, how strong
 * they are, and for the serving cell how far away it is (timing advance).
 */
class CellInfoDataSource(context: Context) {

    private val appContext = context.applicationContext
    private val telephony = appContext.getSystemService<TelephonyManager>()

    /** False on a device without a modem, such as a Wi-Fi-only tablet. */
    val hasTelephony: Boolean get() = telephony != null &&
        appContext.packageManager.hasSystemFeature("android.hardware.telephony")

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun cells(): Flow<List<CellTower>> = flow {
        val manager = telephony ?: return@flow
        while (true) {
            emit(manager.readCells().mapNotNull { it.toCellTower() }.sortedByDescending { it.registered })
            delay(POLL_INTERVAL_MS)
        }
    }

    /** Asks for a fresh reading where the platform allows it; the cached list can be minutes old. */
    @SuppressLint("MissingPermission")
    private suspend fun TelephonyManager.readCells(): List<CellInfo> {
        val cached = { runCatching { allCellInfo }.getOrNull().orEmpty() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return cached()
        return withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val callback = object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                        if (cont.isActive) cont.resume(cellInfo)
                    }

                    override fun onError(errorCode: Int, detail: Throwable?) {
                        if (cont.isActive) cont.resume(cached())
                    }
                }
                runCatching { requestCellInfoUpdate(appContext.mainExecutor, callback) }
                    .onFailure { if (cont.isActive) cont.resume(cached()) }
            }
        } ?: cached()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val REFRESH_TIMEOUT_MS = 3_000L
    }
}
