// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import androidx.annotation.RequiresApi
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.domain.TimingAdvance

/*
 * CellInfo from the modem to CellTower for the page: one function per radio technology, each
 * reading only what its Android version provides.
 */

/** The cell as the app shows it, or null for a technology it does not list (CDMA, TD-SCDMA). */
internal fun CellInfo.toCellTower(): CellTower? = when {
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

/** GSM bit error rate classes RXQUAL 0..7 (3GPP TS 45.008); 99 means not known. */
private val BIT_ERROR_RATE_CLASSES = 0..7
