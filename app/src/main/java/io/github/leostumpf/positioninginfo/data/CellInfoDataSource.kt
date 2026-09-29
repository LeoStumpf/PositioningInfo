// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.domain.TimingAdvance
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

private fun Int.valid(): Int? = takeIf { it != CellInfo.UNAVAILABLE && it != Int.MIN_VALUE }
private fun Long.valid(): Long? = takeIf { it != CellInfo.UNAVAILABLE_LONG && it != Long.MAX_VALUE }

private fun network(mcc: String?, mnc: String?): String? =
    if (mcc != null && mnc != null) "$mcc-$mnc" else null

private fun identity(areaLabel: String, area: Int?, cellLabel: String, cell: Long?): String? =
    listOfNotNull(area?.let { "$areaLabel $it" }, cell?.let { "$cellLabel $it" })
        .joinToString(" · ")
        .ifEmpty { null }

private fun CellInfo.toCellTower(): CellTower? = when {
    this is CellInfoLte -> CellTower(
        technology = "LTE",
        registered = isRegistered,
        network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            network(cellIdentity.mccString, cellIdentity.mncString)
        } else {
            null
        },
        identity = identity("TAC", cellIdentity.tac.valid(), "CI", cellIdentity.ci.valid()?.toLong()),
        physicalId = cellIdentity.pci.valid(),
        physicalIdLabel = "PCI",
        signalDbm = cellSignalStrength.dbm.valid(),
        timingAdvanceDistanceM = cellSignalStrength.timingAdvance.valid()
            ?.takeIf { isRegistered }
            ?.let(TimingAdvance::lteMetres),
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this is CellInfoNr -> {
        val id = cellIdentity as CellIdentityNr
        val signal = cellSignalStrength as CellSignalStrengthNr
        CellTower(
            technology = "5G NR",
            registered = isRegistered,
            network = network(id.mccString, id.mncString),
            identity = identity("TAC", id.tac.valid(), "NCI", id.nci.valid()),
            physicalId = id.pci.valid(),
            physicalIdLabel = "PCI",
            signalDbm = signal.dbm.valid(),
            timingAdvanceDistanceM = if (isRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                signal.timingAdvanceMicros.valid()?.let(TimingAdvance::nrMetres)
            } else {
                null
            },
        )
    }

    this is CellInfoGsm -> CellTower(
        technology = "GSM",
        registered = isRegistered,
        network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            network(cellIdentity.mccString, cellIdentity.mncString)
        } else {
            null
        },
        identity = identity("LAC", cellIdentity.lac.valid(), "CID", cellIdentity.cid.valid()?.toLong()),
        physicalId = cellIdentity.bsic.valid(),
        physicalIdLabel = "BSIC",
        signalDbm = cellSignalStrength.dbm.valid(),
        timingAdvanceDistanceM = cellSignalStrength.timingAdvance.valid()
            ?.takeIf { isRegistered }
            ?.let(TimingAdvance::gsmMetres),
    )

    this is CellInfoWcdma -> CellTower(
        technology = "UMTS",
        registered = isRegistered,
        network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            network(cellIdentity.mccString, cellIdentity.mncString)
        } else {
            null
        },
        identity = identity("LAC", cellIdentity.lac.valid(), "CID", cellIdentity.cid.valid()?.toLong()),
        physicalId = cellIdentity.psc.valid(),
        physicalIdLabel = "PSC",
        signalDbm = cellSignalStrength.dbm.valid(),
        timingAdvanceDistanceM = null,
    )

    else -> null
}
