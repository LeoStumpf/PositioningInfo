// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.data.model.AccessPoint
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.domain.formatAgo
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.DetailRow
import io.github.leostumpf.positioninginfo.ui.common.DetailSheet
import io.github.leostumpf.positioninginfo.ui.common.HeroValue
import io.github.leostumpf.positioninginfo.ui.common.InfoCard
import io.github.leostumpf.positioninginfo.ui.common.LevelBar
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.QuietButton
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.CondensedFamily
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Positioning without satellites — what the network provider reports, how good it really
 * is, and the Wi-Fi and cell data it is built from.
 */
@Composable
fun NetworkScreen(state: NetworkUiState, modifier: Modifier = Modifier) {
    var showAllAps by rememberSaveable { mutableStateOf(false) }
    var selectedCell by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAp by rememberSaveable { mutableStateOf<String?>(null) }
    PageScaffold(Page.NETWORK, modifier) {
        item {
            Text(
                "Satellites are not the only way a phone finds itself. It also recognises the radio " +
                    "signals around it — Wi-Fi access points and the towers of the mobile (cellular) " +
                    "network — and looks up where they are. That works indoors, within a second and with " +
                    "little battery, but is less precise. This page shows that position, how far off it " +
                    "really is, and the signals it is built from.",
                style = BodyStyle.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        section("Position from Wi-Fi and cell towers")
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroValue(
                    value = state.accuracyM?.let { "±${distanceParts(it.toDouble()).first}" } ?: DASH,
                    unit = state.accuracyM?.let { distanceParts(it.toDouble()).second },
                    caption = when {
                        !state.providerEnabled -> null

                        state.ageMs == null -> "Waiting for a network position…"

                        else -> listOfNotNull(
                            "Claimed accuracy",
                            state.source?.let { "based on $it" },
                            formatAgo(state.ageMs),
                        ).joinToString(" · ")
                    },
                )
                if (!state.providerEnabled) {
                    Notice(
                        "Network location is switched off. It is under Settings › Location, usually as Location " +
                            "services › Location accuracy (the name varies by phone).",
                        tone = Tone.DEGRADED,
                    )
                }
                Comparison(state)
            }
        }

        section("Position sources", trailing = "offset from GNSS")
        items(state.sources, key = { it.name }) { SourceRowView(it) }
        item {
            Note(
                "Apps usually get the fused position, which blends GNSS, Wi-Fi, cells and motion sensors. " +
                    "That is why a maps app can show you a few metres from the raw GNSS fix.",
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        section("Location providers", trailing = state.providers.size.takeIf { it > 0 }?.toString())
        items(state.providers, key = { "provider-" + it.name }) { p ->
            ValueRow(
                p.name,
                if (p.enabled) "on" else "off",
                detail = listOfNotNull(p.role, p.quality, p.capabilities).joinToString("\n"),
                valueColor = if (p.enabled) null else Palette.TextTertiary,
                divider = p != state.providers.last(),
            )
        }
        item {
            Note(
                "What each source Android offers apps declares about itself. \"passive\" never starts a " +
                    "search — it hands on fixes some other app asked for.",
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        section("Mobile network · cell towers", trailing = state.cells.size.takeIf { it > 0 }?.toString())
        item {
            Note(
                "The towers of the mobile network the phone can hear. Their known positions give a rough " +
                    "location; the serving tower's timing also tells its distance. Tap one for details.",
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        when {
            !state.hasTelephony -> item { Notice("This device has no mobile radio.") }

            state.cells.isEmpty() -> item { Notice("No cells reported. Is a SIM inserted and airplane mode off?") }

            else -> {
                if (state.cells.none { it.registered }) {
                    item {
                        Box(Modifier.padding(bottom = 6.dp)) {
                            Notice(
                                "No serving cell — no SIM, or out of service. The modem still measures the " +
                                    "towers around it.",
                            )
                        }
                    }
                }
                items(state.cells) { cell -> CellRow(cell) { selectedCell = cell.key() } }
            }
        }

        section("Wi-Fi access points", trailing = state.accessPoints.size.takeIf { it > 0 }?.toString())
        item {
            Note(
                "Wi-Fi networks in range, strongest first. The phone does not need to connect: their " +
                    "addresses alone, looked up in a database, place it within tens of metres. Tap one for details.",
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        when {
            !state.wifiAvailable -> item {
                Notice(
                    "Wi-Fi is off and background Wi-Fi scanning is disabled, so no access points are visible.",
                )
            }

            state.accessPoints.isEmpty() -> item { Notice("No access points found yet.") }

            else -> {
                val shown = if (showAllAps) state.accessPoints else state.accessPoints.take(AP_PREVIEW)
                items(shown, key = { it.bssid }) { ap -> AccessPointRow(ap) { selectedAp = ap.bssid } }
                if (state.accessPoints.size > AP_PREVIEW) {
                    item {
                        QuietButton(
                            if (showAllAps) "Show fewer" else "Show all ${state.accessPoints.size}",
                            onClick = { showAllAps = !showAllAps },
                        )
                    }
                }
            }
        }
    }

    selectedCell?.let { key ->
        val cell = state.cells.firstOrNull { it.key() == key }
        if (cell == null) LaunchedEffect(key) { selectedCell = null } else CellSheet(cell) { selectedCell = null }
    }
    selectedAp?.let { bssid ->
        val ap = state.accessPoints.firstOrNull { it.bssid == bssid }
        if (ap == null) LaunchedEffect(bssid) { selectedAp = null } else AccessPointSheet(ap) { selectedAp = null }
    }
}

/** Stable enough to keep a sheet open across the 5-second cell refresh. */
private fun CellTower.key() = "$technology|$network|$identity|$physicalId|$channel"

@Composable
private fun CellSheet(cell: CellTower, onDismiss: () -> Unit) {
    val rows = buildList {
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
            add(
                DetailRow(q.name, "${q.value}${if (q.unit.isNotEmpty()) " ${q.unit}" else ""}", qualityMeaning(q.name)),
            )
        }
        if (cell.timingAdvanceSteps != null || cell.timingAdvanceDistanceM != null) {
            add(
                DetailRow(
                    "Timing advance",
                    listOfNotNull(
                        cell.timingAdvanceSteps?.let { "$it steps" },
                        cell.timingAdvanceDistanceM?.let { "≈ ${formatDistance(it)}" },
                    ).joinToString(" · "),
                    "How early the phone transmits so its signal arrives on time — the round trip, so the " +
                        "distance to the tower.",
                ),
            )
        }
    }
    DetailSheet(
        title = listOfNotNull(cell.operatorName, cell.technology).joinToString(" · "),
        subtitle = if (cell.registered) "Serving cell tower" else "Neighbouring cell tower",
        rows = rows,
        onDismiss = onDismiss,
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

@Composable
private fun AccessPointSheet(ap: AccessPoint, onDismiss: () -> Unit) {
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

/** 2.4 GHz channels start at 2412 MHz, 5 GHz at 5000, 6 GHz at 5950 — all in 5 MHz steps. */
internal fun wifiChannel(mhz: Int): Int? = when (mhz) {
    2484 -> 14
    in 2412..2472 -> (mhz - 2407) / 5
    in 5160..5885 -> (mhz - 5000) / 5
    in 5955..7115 -> (mhz - 5950) / 5
    else -> null
}

/** Where the network position lies relative to GNSS, drawn to scale inside its claimed circle. */
@Composable
private fun Comparison(state: NetworkUiState) {
    val c = state.comparison
    InfoCard {
        if (c == null) {
            Text("REAL ERROR VS GNSS", style = OverlineStyle, color = Palette.TextTertiary)
            Text(
                state.comparisonUnavailableReason,
                style = CaptionStyle,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@InfoCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val claimed = state.accuracyM?.toDouble()
            Canvas(
                Modifier.size(88.dp).semantics {
                    contentDescription = "Network position ${formatDistance(c.distanceM)} from the GNSS fix" +
                        (claimed?.let { ", claimed accuracy ${formatDistance(it)}" } ?: "")
                },
            ) {
                val centre = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2 - 4.dp.toPx()
                val scale = if (claimed != null && claimed > 0) {
                    r / maxOf(
                        claimed,
                        c.distanceM,
                    )
                } else {
                    r / maxOf(c.distanceM, 1.0)
                }
                claimed?.let {
                    drawCircle(
                        Palette.TextTertiary,
                        (it * scale).toFloat(),
                        centre,
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                    )
                }
                drawCircle(Palette.TextPrimary, 4.dp.toPx(), centre)
                val offset = min((c.distanceM * scale).toFloat(), r)
                drawCircle(
                    if (c.withinClaimed == false) Palette.Degraded else Palette.Good,
                    3.dp.toPx(),
                    Offset(centre.x + offset * 0.8f, centre.y - offset * 0.6f),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("REAL ERROR VS GNSS", style = OverlineStyle, color = Palette.TextTertiary)
                Text(
                    buildAnnotatedString {
                        val (value, unit) = distanceParts(c.distanceM)
                        append(value)
                        withStyle(SpanStyle(fontSize = 16.sp, color = Palette.TextSecondary)) { append(" $unit off") }
                    },
                    style = BodyStyle.copy(fontFamily = CondensedFamily, fontSize = 32.sp, lineHeight = 36.sp),
                    color = Palette.TextPrimary,
                )
                c.withinClaimed?.let {
                    Text(
                        if (it) "Inside the claimed circle" else "Outside the claimed circle",
                        style = CaptionStyle,
                        color = if (it) Palette.Good else Palette.Degraded,
                    )
                }
                Text(
                    "GNSS reference ±${c.gnssAccuracyM.roundToInt()} m",
                    style = CaptionStyle.copy(fontSize = 12.sp),
                    color = Palette.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun SourceRowView(source: SourceRow) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(source.name, style = BodyStyle, color = Palette.TextPrimary)
                Text(
                    when {
                        !source.available -> "not available or switched off"

                        source.accuracyM == null -> source.description

                        else -> listOfNotNull(
                            "±${formatDistance(source.accuracyM.toDouble())}",
                            source.ageMs?.let { formatAgo(it) },
                            if (source.isMock) "SIMULATED" else null,
                        ).joinToString(" · ")
                    },
                    style = CaptionStyle.copy(fontSize = 12.sp),
                    color = if (source.isMock) Palette.Bad else Palette.TextTertiary,
                )
            }
            Text(
                when {
                    source.isReference -> if (source.accuracyM != null) "reference" else DASH
                    source.offsetM != null -> formatDistance(source.offsetM)
                    else -> DASH
                },
                style = DataStyle,
                color = when {
                    source.isReference -> Palette.TextTertiary
                    source.isOutsideItsClaim() -> Palette.Degraded
                    else -> Palette.TextPrimary
                },
            )
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

@Composable
private fun CellRow(cell: CellTower, onClick: () -> Unit) {
    Column(Modifier.clickable(onClickLabel = "Show cell details", onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                cell.technology.replace("5G NR", "NR"),
                style = DataStyle.copy(fontSize = 11.sp),
                color = if (cell.registered) Palette.TextPrimary else Palette.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(
                    min = 44.dp,
                ).border(
                    1.dp,
                    if (cell.registered) Palette.TextPrimary else Palette.Outline,
                    RoundedCornerShape(6.dp),
                ).padding(vertical = 2.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOfNotNull(
                        cell.operatorName ?: cell.network,
                        cell.cellId,
                    ).joinToString(" · ").ifEmpty { cell.technology },
                    style = DataStyle.copy(fontSize = 13.sp),
                    color = Palette.TextPrimary,
                )
                Text(
                    listOfNotNull(
                        if (cell.registered) "serving" else "neighbour",
                        cell.area?.takeIf { cell.cellId != null },
                        cell.physicalId?.let { "${cell.physicalIdLabel} $it" },
                        cell.timingAdvanceDistanceM?.let { "tower ≈ ${formatDistance(it)}" },
                    ).joinToString(" · "),
                    style = CaptionStyle.copy(fontSize = 12.sp),
                    color = Palette.TextTertiary,
                )
            }
            Text(
                buildAnnotatedString {
                    append(cell.signalDbm?.toString()?.replace("-", "−") ?: DASH)
                    withStyle(SpanStyle(fontSize = 11.sp, color = Palette.TextTertiary)) { append(" dBm") }
                },
                style = DataStyle.copy(fontSize = 13.sp),
                color = Palette.TextPrimary,
            )
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

@Composable
private fun AccessPointRow(ap: AccessPoint, onClick: () -> Unit) {
    Column(Modifier.clickable(onClickLabel = "Show access point details", onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                ap.ssid ?: "hidden · ${ap.bssid}",
                style = BodyStyle.copy(fontSize = 14.sp),
                color = if (ap.ssid != null) Palette.TextPrimary else Palette.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(band(ap.frequencyMhz), style = DataStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary)
            // −30 dBm is as strong as Wi-Fi gets, −90 is the edge.
            val fraction = ((ap.rssiDbm + 90) / 60f).coerceIn(0f, 1f)
            LevelBar(
                fraction,
                when {
                    ap.rssiDbm >= RSSI_GOOD_DBM -> Palette.TextPrimary
                    ap.rssiDbm >= RSSI_FAIR_DBM -> Palette.TextSecondary
                    else -> Palette.TextTertiary
                },
                Modifier.width(56.dp),
            )
            Text(
                ap.rssiDbm.toString().replace("-", "−"),
                style = DataStyle.copy(fontSize = 13.sp),
                color = Palette.TextPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 32.dp),
            )
        }
        HorizontalDivider(color = Palette.RowDivider)
    }
}

private const val AP_PREVIEW = 8

/** Wi-Fi signal tiers: −60 dBm and up is strong, down to −75 usable, below that weak. */
private const val RSSI_GOOD_DBM = -60
private const val RSSI_FAIR_DBM = -75

/** The source's position lies further from GNSS than the accuracy it claims. */
private fun SourceRow.isOutsideItsClaim(): Boolean {
    val offset = offsetM ?: return false
    val claimed = accuracyM ?: return false
    return offset > claimed
}
