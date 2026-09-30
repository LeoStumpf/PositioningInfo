// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellIdentity
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.data.model.SignalMeasure
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

// UNAVAILABLE and UNAVAILABLE_LONG are compile-time constants (Integer and Long MAX_VALUE),
// inlined into the app, so reading them is safe on every version.
@SuppressLint("InlinedApi")
private fun Int.valid(): Int? = takeIf { it != CellInfo.UNAVAILABLE && it != Int.MIN_VALUE }
@SuppressLint("InlinedApi")
private fun Long.valid(): Long? = takeIf { it != CellInfo.UNAVAILABLE_LONG && it != Long.MAX_VALUE }

private fun network(mcc: String?, mnc: String?): String? =
    if (mcc != null && mnc != null) "$mcc-$mnc" else null

private fun labelled(label: String, value: Number?): String? = value?.let { "$label $it" }

// CellInfo.getCellIdentity() and getCellSignalStrength() exist on the base class only from
// Android 11; below that, calling them throws NoSuchMethodError. So both are read from the
// typed subclass inside each branch, never from the base type. The typed identities only
// share the CellIdentity base class from Android 9, hence the cast behind the check.
private fun operatorOf(id: Any): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        (id as CellIdentity).operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
    } else {
        null
    }

private fun levelOf(s: CellSignalStrength): Int? = s.level.takeIf { it in 0..4 }

private fun CellInfo.toCellTower(): CellTower? {
    val bandsOf: (() -> IntArray?) -> List<Int> = { get ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { get()?.toList() }.getOrNull().orEmpty() else emptyList()
    }
    return when {
        this is CellInfoLte -> {
            val id = cellIdentity
            val s = cellSignalStrength
            CellTower(
                technology = "LTE",
                registered = isRegistered,
                network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) network(id.mccString, id.mncString) else null,
                area = labelled("TAC", id.tac.valid()),
                cellId = labelled("CI", id.ci.valid()?.toLong()),
                physicalId = id.pci.valid(),
                physicalIdLabel = "PCI",
                signalDbm = s.dbm.valid(),
                timingAdvanceDistanceM = s.timingAdvance.valid()?.takeIf { isRegistered }?.let(TimingAdvance::lteMetres),
                operatorName = operatorOf(id),
                channel = id.earfcn.valid(),
                channelLabel = "EARFCN",
                bands = bandsOf { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands else null },
                quality = listOfNotNull(
                    s.rsrp.valid()?.let { SignalMeasure("RSRP", it, "dBm") },
                    s.rsrq.valid()?.let { SignalMeasure("RSRQ", it, "dB") },
                    s.rssnr.valid()?.let { SignalMeasure("SINR", it, "dB") },
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) s.rssi.valid()?.let { SignalMeasure("RSSI", it, "dBm") } else null,
                ),
                level = levelOf(s),
                timingAdvanceSteps = s.timingAdvance.valid()?.takeIf { isRegistered },
            )
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this is CellInfoNr -> {
            val id = cellIdentity as CellIdentityNr
            val s = cellSignalStrength as CellSignalStrengthNr
            CellTower(
                technology = "5G NR",
                registered = isRegistered,
                network = network(id.mccString, id.mncString),
                area = labelled("TAC", id.tac.valid()),
                cellId = labelled("NCI", id.nci.valid()),
                physicalId = id.pci.valid(),
                physicalIdLabel = "PCI",
                signalDbm = s.dbm.valid(),
                timingAdvanceDistanceM = if (isRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    s.timingAdvanceMicros.valid()?.let(TimingAdvance::nrMetres)
                } else {
                    null
                },
                operatorName = operatorOf(id),
                channel = id.nrarfcn.valid(),
                channelLabel = "NR-ARFCN",
                bands = bandsOf { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands else null },
                quality = listOfNotNull(
                    s.ssRsrp.valid()?.let { SignalMeasure("SS-RSRP", it, "dBm") },
                    s.ssRsrq.valid()?.let { SignalMeasure("SS-RSRQ", it, "dB") },
                    s.ssSinr.valid()?.let { SignalMeasure("SS-SINR", it, "dB") },
                ),
                level = levelOf(s),
            )
        }

        this is CellInfoGsm -> {
            val id = cellIdentity
            val s = cellSignalStrength
            CellTower(
                technology = "GSM",
                registered = isRegistered,
                network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) network(id.mccString, id.mncString) else null,
                area = labelled("LAC", id.lac.valid()),
                cellId = labelled("CID", id.cid.valid()?.toLong()),
                physicalId = id.bsic.valid(),
                physicalIdLabel = "BSIC",
                signalDbm = s.dbm.valid(),
                timingAdvanceDistanceM = s.timingAdvance.valid()?.takeIf { isRegistered }?.let(TimingAdvance::gsmMetres),
                operatorName = operatorOf(id),
                channel = id.arfcn.valid(),
                channelLabel = "ARFCN",
                quality = listOfNotNull(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) s.rssi.valid()?.let { SignalMeasure("RSSI", it, "dBm") } else null,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) s.bitErrorRate.takeIf { it in 0..7 }?.let { SignalMeasure("Bit error rate class", it, "") } else null,
                ),
                level = levelOf(s),
                timingAdvanceSteps = s.timingAdvance.valid()?.takeIf { isRegistered },
            )
        }

        this is CellInfoWcdma -> {
            val id = cellIdentity
            val s = cellSignalStrength
            CellTower(
                technology = "UMTS",
                registered = isRegistered,
                network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) network(id.mccString, id.mncString) else null,
                area = labelled("LAC", id.lac.valid()),
                cellId = labelled("CID", id.cid.valid()?.toLong()),
                physicalId = id.psc.valid(),
                physicalIdLabel = "PSC",
                signalDbm = s.dbm.valid(),
                timingAdvanceDistanceM = null,
                operatorName = operatorOf(id),
                channel = id.uarfcn.valid(),
                channelLabel = "UARFCN",
                quality = listOfNotNull(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) s.ecNo.valid()?.let { SignalMeasure("Ec/No", it, "dB") } else null,
                ),
                level = levelOf(s),
            )
        }

        else -> null
    }
}
