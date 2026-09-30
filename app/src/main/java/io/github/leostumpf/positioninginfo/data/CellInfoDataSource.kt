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
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
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

private fun network(mcc: String?, mnc: String?): String? = if (mcc != null && mnc != null) "$mcc-$mnc" else null

private fun labelled(label: String, value: Number?): String? = value?.let { "$label $it" }

// CellInfo.getCellIdentity() and getCellSignalStrength() exist on the base class only from
// Android 11; below that, calling them throws NoSuchMethodError. So both are read from the
// typed subclass inside each branch, never from the base type. The typed identities only
// share the CellIdentity base class from Android 9, hence the cast behind the check.
private fun operatorOf(id: Any): String? {
    if (!sdk(Build.VERSION_CODES.P)) return null
    return (id as CellIdentity).operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
}

private fun levelOf(s: CellSignalStrength): Int? = s.level.takeIf { it in SIGNAL_LEVELS }

/** Android's bars: none or unknown, poor, moderate, good, great. */
private val SIGNAL_LEVELS = CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN..CellSignalStrength.SIGNAL_STRENGTH_GREAT

/** GSM bit error rate classes RXQUAL 0..7 (3GPP TS 45.008); 99 means not known. */
private val BIT_ERROR_RATE_CLASSES = 0..7

/** True on Android [version] or newer; annotated so lint's API check sees the guard. */
@ChecksSdkIntAtLeast(parameter = 0)
private fun sdk(version: Int): Boolean = Build.VERSION.SDK_INT >= version

/** A quality figure, or null when the modem does not report it. */
private fun measure(name: String, value: Int?, unit: String): SignalMeasure? =
    value?.let { SignalMeasure(name, it, unit) }

/** The frequency bands a cell identity lists, from Android 11; empty before. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R, lambda = 0)
private fun bandsOf(read: () -> IntArray): List<Int> =
    if (sdk(Build.VERSION_CODES.R)) runCatching { read().toList() }.getOrDefault(emptyList()) else emptyList()

/** MCC-MNC of a cell identity, which Android exposes as strings from version 9. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P, lambda = 0)
private fun networkOf(mcc: () -> String?, mnc: () -> String?): String? =
    if (sdk(Build.VERSION_CODES.P)) network(mcc(), mnc()) else null

private fun CellInfo.toCellTower(): CellTower? = when {
    this is CellInfoLte -> toCellTower()
    sdk(Build.VERSION_CODES.Q) && this is CellInfoNr -> toCellTower()
    this is CellInfoGsm -> toCellTower()
    this is CellInfoWcdma -> toCellTower()
    else -> null
}

private fun CellInfoLte.toCellTower(): CellTower {
    val id = cellIdentity
    val s = cellSignalStrength
    val timingAdvance = s.timingAdvance.valid()?.takeIf { isRegistered }
    return CellTower(
        technology = "LTE",
        registered = isRegistered,
        network = networkOf({ id.mccString }, { id.mncString }),
        area = labelled("TAC", id.tac.valid()),
        cellId = labelled("CI", id.ci.valid()?.toLong()),
        physicalId = id.pci.valid(),
        physicalIdLabel = "PCI",
        signalDbm = s.dbm.valid(),
        timingAdvanceDistanceM = timingAdvance?.let(TimingAdvance::lteMetres),
        operatorName = operatorOf(id),
        channel = id.earfcn.valid(),
        channelLabel = "EARFCN",
        bands = bandsOf { id.bands },
        quality = listOfNotNull(
            measure("RSRP", s.rsrp.valid(), "dBm"),
            measure("RSRQ", s.rsrq.valid(), "dB"),
            measure("SINR", s.rssnr.valid(), "dB"),
            measure("RSSI", if (sdk(Build.VERSION_CODES.Q)) s.rssi.valid() else null, "dBm"),
        ),
        level = levelOf(s),
        timingAdvanceSteps = timingAdvance,
    )
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun CellInfoNr.toCellTower(): CellTower {
    val id = cellIdentity as CellIdentityNr
    val s = cellSignalStrength as CellSignalStrengthNr
    // 5G's timing advance is readable from Android 14 on.
    val timingAdvanceReadable = isRegistered && sdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    val timingAdvanceMicros = if (timingAdvanceReadable) s.timingAdvanceMicros else null
    return CellTower(
        technology = "5G NR",
        registered = isRegistered,
        network = network(id.mccString, id.mncString),
        area = labelled("TAC", id.tac.valid()),
        cellId = labelled("NCI", id.nci.valid()),
        physicalId = id.pci.valid(),
        physicalIdLabel = "PCI",
        signalDbm = s.dbm.valid(),
        timingAdvanceDistanceM = timingAdvanceMicros?.valid()?.let(TimingAdvance::nrMetres),
        operatorName = operatorOf(id),
        channel = id.nrarfcn.valid(),
        channelLabel = "NR-ARFCN",
        bands = bandsOf { id.bands },
        quality = listOfNotNull(
            measure("SS-RSRP", s.ssRsrp.valid(), "dBm"),
            measure("SS-RSRQ", s.ssRsrq.valid(), "dB"),
            measure("SS-SINR", s.ssSinr.valid(), "dB"),
        ),
        level = levelOf(s),
    )
}

private fun CellInfoGsm.toCellTower(): CellTower {
    val id = cellIdentity
    val s = cellSignalStrength
    val timingAdvance = s.timingAdvance.valid()?.takeIf { isRegistered }
    val bitErrorRate = if (sdk(Build.VERSION_CODES.Q)) s.bitErrorRate.takeIf { it in BIT_ERROR_RATE_CLASSES } else null
    return CellTower(
        technology = "GSM",
        registered = isRegistered,
        network = networkOf({ id.mccString }, { id.mncString }),
        area = labelled("LAC", id.lac.valid()),
        cellId = labelled("CID", id.cid.valid()?.toLong()),
        physicalId = id.bsic.valid(),
        physicalIdLabel = "BSIC",
        signalDbm = s.dbm.valid(),
        timingAdvanceDistanceM = timingAdvance?.let(TimingAdvance::gsmMetres),
        operatorName = operatorOf(id),
        channel = id.arfcn.valid(),
        channelLabel = "ARFCN",
        quality = listOfNotNull(
            measure("RSSI", if (sdk(Build.VERSION_CODES.R)) s.rssi.valid() else null, "dBm"),
            measure("Bit error rate class", bitErrorRate, ""),
        ),
        level = levelOf(s),
        timingAdvanceSteps = timingAdvance,
    )
}

private fun CellInfoWcdma.toCellTower(): CellTower {
    val id = cellIdentity
    val s = cellSignalStrength
    return CellTower(
        technology = "UMTS",
        registered = isRegistered,
        network = networkOf({ id.mccString }, { id.mncString }),
        area = labelled("LAC", id.lac.valid()),
        cellId = labelled("CID", id.cid.valid()?.toLong()),
        physicalId = id.psc.valid(),
        physicalIdLabel = "PSC",
        signalDbm = s.dbm.valid(),
        timingAdvanceDistanceM = null,
        operatorName = operatorOf(id),
        channel = id.uarfcn.valid(),
        channelLabel = "UARFCN",
        quality = listOfNotNull(measure("Ec/No", if (sdk(Build.VERSION_CODES.R)) s.ecNo.valid() else null, "dB")),
        level = levelOf(s),
    )
}
