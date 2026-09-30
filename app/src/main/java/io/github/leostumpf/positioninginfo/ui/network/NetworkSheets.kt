// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.leostumpf.positioninginfo.data.model.AccessPoint
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.domain.formatAgo
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.DetailRow
import io.github.leostumpf.positioninginfo.ui.common.DetailSheet

/** Everything the modem reports about one cell, with a word on what each figure means. */
@Composable
internal fun CellSheet(cell: CellTower, onDismiss: () -> Unit) {
    val rows = cellIdentityRows(cell) + cellRadioRows(cell)
    DetailSheet(
        title = listOfNotNull(cell.operatorName, cell.technology).joinToString(" · "),
        subtitle = if (cell.registered) "Serving cell tower" else "Neighbouring cell tower",
        rows = rows,
        onDismiss = onDismiss,
    )
}

/** Who the cell is: role, technology, operator and the codes position databases look up. */
private fun cellIdentityRows(cell: CellTower): List<DetailRow> = buildList {
    add(
        DetailRow(
            "Role",
            if (cell.registered) "serving" else "neighbour",
            if (cell.registered) {
                "The cell the phone is attached to."
            } else {
                "A cell the phone measures for handover but is not attached to."
            },
        ),
    )
    add(DetailRow("Technology", cell.technology))
    add(DetailRow("Operator", cell.operatorName ?: "not broadcast"))
    cell.network?.let {
        add(
            DetailRow(
                "Network code (MCC-MNC)",
                it,
                "Country code, then operator code — 310 is the USA, 234 the UK, 262 Germany.",
            ),
        )
    }
    cell.identity?.let {
        add(
            DetailRow(
                "Area and cell identity",
                it,
                "Tracking/location area code and the cell's unique number: what position databases look up.",
            ),
        )
    }
}

/** How the cell is heard: physical code, channel, signal figures and timing advance. */
private fun cellRadioRows(cell: CellTower): List<DetailRow> = buildList {
    cell.physicalId?.let {
        add(
            DetailRow(
                cell.physicalIdLabel,
                it.toString(),
                "Short physical code that tells neighbouring cells apart on the air.",
            ),
        )
    }
    cell.channel?.let {
        add(
            DetailRow(
                cell.channelLabel ?: "Channel",
                it.toString() + if (cell.bands.isNotEmpty()) " · band ${cell.bands.joinToString()}" else "",
                "The radio channel number; the band says which frequency range.",
            ),
        )
    }
    add(
        DetailRow(
            "Signal",
            (cell.signalDbm?.let { "$it dBm" } ?: DASH) + (cell.level?.let { " · $it of 4 bars" } ?: ""),
        ),
    )
    cell.quality.forEach { q ->
        add(DetailRow(q.name, "${q.value}${if (q.unit.isNotEmpty()) " ${q.unit}" else ""}", qualityMeaning(q.name)))
    }
    timingAdvanceRow(cell)?.let(::add)
}

/** Timing advance, when the modem reports it: how far away the serving tower is. */
private fun timingAdvanceRow(cell: CellTower): DetailRow? {
    if (cell.timingAdvanceSteps == null && cell.timingAdvanceDistanceM == null) return null
    return DetailRow(
        "Timing advance",
        listOfNotNull(
            cell.timingAdvanceSteps?.let { "$it steps" },
            cell.timingAdvanceDistanceM?.let { "≈ ${formatDistance(it)}" },
        ).joinToString(" · "),
        "How early the phone transmits so its signal arrives on time — the round trip, so the " +
            "distance to the tower.",
    )
}

private fun qualityMeaning(name: String): String? = when (name) {
    "RSRP", "SS-RSRP" -> "Received power of the reference signal. Above −80 excellent, below −110 the cell edge."
    "RSRQ", "SS-RSRQ" -> "Signal quality relative to everything received. Above −10 good, below −15 poor."
    "SINR", "SS-SINR" -> "Signal against interference and noise. Above 13 good, below 0 poor."
    "RSSI" -> "Total received power in the channel, noise and other cells included."
    "Ec/No" -> "UMTS signal quality. Above −6 good, below −14 poor."
    "Bit error rate class" -> "0 is clean, 7 is barely usable."
    else -> null
}

/** What the scan reports about one access point. */
@Composable
internal fun AccessPointSheet(ap: AccessPoint, onDismiss: () -> Unit) {
    val rows = buildList {
        add(
            DetailRow(
                "Name (SSID)",
                ap.ssid ?: "hidden",
                if (ap.ssid == null) "The network does not broadcast its name." else null,
            ),
        )
        add(
            DetailRow(
                "Address (BSSID)",
                ap.bssid,
                "The access point's hardware address — what position databases look up.",
            ),
        )
        add(
            DetailRow(
                "Signal",
                "${ap.rssiDbm} dBm",
                "Above −60 strong, below −85 barely usable. Stronger usually means closer.",
            ),
        )
        add(
            DetailRow(
                "Band and channel",
                "${band(ap.frequencyMhz)} · channel ${wifiChannel(ap.frequencyMhz) ?: "?"} · ${ap.frequencyMhz} MHz",
            ),
        )
        ap.channelWidthMhz?.let { add(DetailRow("Channel width", "$it MHz")) }
        ap.standard?.let { add(DetailRow("Standard", it)) }
        add(DetailRow("Security", ap.security))
        add(
            DetailRow(
                "Round-trip-time ranging",
                if (ap.rttResponder) "supported" else "no",
                "802.11mc access points let a phone measure its distance to them to about a metre.",
            ),
        )
        ap.ageMs?.let { add(DetailRow("Last seen", formatAgo(it))) }
    }
    DetailSheet(
        title = ap.ssid ?: "Hidden network",
        subtitle = "Wi-Fi access point",
        rows = rows,
        onDismiss = onDismiss,
    )
}

/** The Wi-Fi channel number of a centre frequency, or null outside the channel plans. */
internal fun wifiChannel(mhz: Int): Int? {
    if (mhz == CHANNEL_14_MHZ) return CHANNEL_14
    val plan = CHANNEL_PLANS.firstOrNull { mhz in it.centres } ?: return null
    return (mhz - plan.channelZeroMhz) / CHANNEL_STEP_MHZ
}

/** A band's channels: [centres] they may lie on, counted in 5 MHz steps from [channelZeroMhz]. */
private class ChannelPlan(val centres: IntRange, val channelZeroMhz: Int)

private val CHANNEL_PLANS = listOf(
    ChannelPlan(centres = 2412..2472, channelZeroMhz = 2407),
    ChannelPlan(centres = 5160..5885, channelZeroMhz = 5000),
    ChannelPlan(centres = 5955..7115, channelZeroMhz = 5950),
)
private const val CHANNEL_STEP_MHZ = 5

/** Channel 14 (Japan, 802.11b only) is the odd one out, 12 MHz above channel 13. */
private const val CHANNEL_14_MHZ = 2484
private const val CHANNEL_14 = 14
