// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.data.model.AccessPoint
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.domain.LocationProviderInfo
import io.github.leostumpf.positioninginfo.domain.formatAgo
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.HeroValue
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
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import kotlin.math.min

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
        networkPositionSection(state)
        sourcesSection(state.sources)
        providersSection(state.providers)
        cellsSection(state, onSelect = { selectedCell = it.key() })
        accessPointsSection(
            state,
            showAll = showAllAps,
            onToggleShowAll = { showAllAps = !showAllAps },
            onSelect = { selectedAp = it.bssid },
        )
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

/** The network provider's position: its claimed accuracy, then its real error against GNSS. */
private fun LazyListScope.networkPositionSection(state: NetworkUiState) {
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
}

/** Every position source side by side, each with its offset from the GNSS fix. */
private fun LazyListScope.sourcesSection(sources: List<SourceRow>) {
    section("Position sources", trailing = "offset from GNSS")
    items(sources, key = { it.name }) { SourceRowView(it) }
    item {
        Note(
            "Apps usually get the fused position, which blends GNSS, Wi-Fi, cells and motion sensors. " +
                "That is why a maps app can show you a few metres from the raw GNSS fix.",
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The location providers Android offers, and what each declares about itself. */
private fun LazyListScope.providersSection(providers: List<LocationProviderInfo>) {
    section("Location providers", trailing = providers.size.takeIf { it > 0 }?.toString())
    items(providers, key = { "provider-" + it.name }) { p ->
        ValueRow(
            p.name,
            if (p.enabled) "on" else "off",
            detail = listOfNotNull(p.role, p.quality, p.capabilities).joinToString("\n"),
            valueColor = if (p.enabled) null else Palette.TextTertiary,
            divider = p != providers.last(),
        )
    }
    item {
        Note(
            "What each source Android offers apps declares about itself. \"passive\" never starts a " +
                "search — it hands on fixes some other app asked for.",
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The cell towers the modem hears, serving cell first; or why there are none. */
private fun LazyListScope.cellsSection(state: NetworkUiState, onSelect: (CellTower) -> Unit) {
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
                    Notice(
                        "No serving cell — no SIM, or out of service. The modem still measures the " +
                            "towers around it.",
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            items(state.cells) { cell -> CellRow(cell) { onSelect(cell) } }
        }
    }
}

/** Wi-Fi access points in range, strongest first, the first [AP_PREVIEW] unless expanded. */
private fun LazyListScope.accessPointsSection(
    state: NetworkUiState,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onSelect: (AccessPoint) -> Unit,
) {
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
            Notice("Wi-Fi is off and background Wi-Fi scanning is disabled, so no access points are visible.")
        }

        state.accessPoints.isEmpty() -> item { Notice("No access points found yet.") }

        else -> {
            val shown = if (showAll) state.accessPoints else state.accessPoints.take(AP_PREVIEW)
            items(shown, key = { it.bssid }) { ap -> AccessPointRow(ap) { onSelect(ap) } }
            if (state.accessPoints.size > AP_PREVIEW) {
                item {
                    QuietButton(
                        if (showAll) "Show fewer" else "Show all ${state.accessPoints.size}",
                        onClick = onToggleShowAll,
                    )
                }
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
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .border(
                        1.dp,
                        if (cell.registered) Palette.TextPrimary else Palette.Outline,
                        RoundedCornerShape(6.dp),
                    )
                    .padding(vertical = 2.dp),
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
