// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.data.model.AccessPoint
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.ui.about.AboutSection
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.DataInventory
import io.github.leostumpf.positioninginfo.ui.common.DataOnThisPhone
import io.github.leostumpf.positioninginfo.ui.common.HeroValue
import io.github.leostumpf.positioninginfo.ui.common.InfoCard
import io.github.leostumpf.positioninginfo.ui.common.LevelBar
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.QuietButton
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.PlexCondensed
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Positioning without satellites — what the network provider reports, how good it really
 * is, and the Wi-Fi and cell data it is built from — followed by the app's About notice.
 */
@Composable
fun NetworkScreen(
    state: NetworkUiState,
    dataInventory: DataInventory,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllAps by rememberSaveable { mutableStateOf(false) }
    PageScaffold(Page.NETWORK, modifier) {
        item {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroValue(
                    value = state.accuracyM?.let { "±${formatDistance(it.toDouble()).substringBefore(' ')}" } ?: DASH,
                    unit = state.accuracyM?.let { formatDistance(it.toDouble()).substringAfter(' ') },
                    caption = when {
                        !state.providerEnabled -> null
                        state.ageMs == null -> "Waiting for a network position…"
                        else -> listOfNotNull("Claimed accuracy", state.source?.let { "from $it" }, formatAge(state.ageMs)).joinToString(" · ")
                    },
                )
                if (!state.providerEnabled) {
                    Notice(
                        "Network location is switched off. On a Pixel: Settings › Location › Location services › Google Location Accuracy.",
                        Tone.DEGRADED,
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

        section("Cell towers")
        when {
            !state.hasTelephony -> item { Notice("This device has no mobile radio.") }
            state.cells.isEmpty() -> item { Notice("No cells reported. Is a SIM inserted and airplane mode off?") }
            else -> {
                if (state.cells.none { it.registered }) {
                    item {
                        Box(Modifier.padding(bottom = 6.dp)) {
                            Notice("No serving cell — no SIM, or out of service. The modem still measures the towers around it.")
                        }
                    }
                }
                items(state.cells) { CellRow(it) }
            }
        }

        section("Wi-Fi access points", trailing = state.accessPoints.size.takeIf { it > 0 }?.toString())
        when {
            !state.wifiAvailable -> item { Notice("Wi-Fi is off and background Wi-Fi scanning is disabled, so no access points are visible.") }
            state.accessPoints.isEmpty() -> item { Notice("No access points found yet.") }
            else -> {
                val shown = if (showAllAps) state.accessPoints else state.accessPoints.take(AP_PREVIEW)
                items(shown, key = { it.bssid }) { AccessPointRow(it) }
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

        section("Data on this phone")
        item { DataOnThisPhone(dataInventory, onClearAllData) }

        section("About")
        item { AboutSection() }
    }
}

/** Where the network position lies relative to GNSS, drawn to scale inside its claimed circle. */
@Composable
private fun Comparison(state: NetworkUiState) {
    val c = state.comparison
    InfoCard {
        if (c == null) {
            Text("REAL ERROR VS GNSS", style = OverlineStyle, color = Palette.TextTertiary)
            Text(state.comparisonUnavailableReason, style = CaptionStyle, color = Palette.TextSecondary, modifier = Modifier.padding(top = 6.dp))
            return@InfoCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val claimed = state.accuracyM?.toDouble()
            Canvas(Modifier.size(88.dp)) {
                val centre = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2 - 4.dp.toPx()
                val scale = if (claimed != null && claimed > 0) r / maxOf(claimed, c.distanceM) else r / maxOf(c.distanceM, 1.0)
                claimed?.let {
                    drawCircle(Palette.TextTertiary, (it * scale).toFloat(), centre, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
                }
                drawCircle(Palette.TextPrimary, 4.dp.toPx(), centre)
                val offset = min((c.distanceM * scale).toFloat(), r)
                drawCircle(
                    if (c.withinClaimed == false) Palette.Degraded else Palette.Good, 3.dp.toPx(),
                    Offset(centre.x + offset * 0.8f, centre.y - offset * 0.6f), style = Stroke(1.5.dp.toPx()),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("REAL ERROR VS GNSS", style = OverlineStyle, color = Palette.TextTertiary)
                Text(
                    buildAnnotatedString {
                        append(formatDistance(c.distanceM).substringBefore(' '))
                        withStyle(SpanStyle(fontSize = 16.sp, color = Palette.TextSecondary)) { append(" ${formatDistance(c.distanceM).substringAfter(' ')} off") }
                    },
                    style = BodyStyle.copy(fontFamily = PlexCondensed, fontSize = 32.sp, lineHeight = 36.sp),
                    color = Palette.TextPrimary,
                )
                c.withinClaimed?.let {
                    Text(
                        if (it) "Inside the claimed circle" else "Outside the claimed circle",
                        style = CaptionStyle, color = if (it) Palette.Good else Palette.Degraded,
                    )
                }
                Text("GNSS reference ±${c.gnssAccuracyM.roundToInt()} m", style = CaptionStyle.copy(fontSize = 12.sp), color = Palette.TextTertiary)
            }
        }
    }
}

@Composable
private fun SourceRowView(source: SourceRow) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(source.name, style = BodyStyle, color = Palette.TextPrimary)
                Text(
                    when {
                        !source.available -> "not available or switched off"
                        source.accuracyM == null -> source.description
                        else -> listOfNotNull(
                            "±${formatDistance(source.accuracyM.toDouble())}",
                            source.ageMs?.let { formatAge(it) },
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
                    source.offsetM != null && source.accuracyM != null && source.offsetM > source.accuracyM -> Palette.Degraded
                    else -> Palette.TextPrimary
                },
            )
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

@Composable
private fun CellRow(cell: CellTower) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                cell.technology.replace("5G NR", "NR"),
                style = DataStyle.copy(fontSize = 11.sp),
                color = if (cell.registered) Palette.TextPrimary else Palette.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp).border(1.dp, if (cell.registered) Palette.TextPrimary else Palette.Outline, RoundedCornerShape(6.dp)).padding(vertical = 2.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOfNotNull(cell.network, cell.identity?.substringAfterLast(" · ")).joinToString(" · ").ifEmpty { cell.technology },
                    style = DataStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary,
                )
                Text(
                    listOfNotNull(
                        if (cell.registered) "serving" else "neighbour",
                        cell.identity?.substringBeforeLast(" · ")?.takeIf { cell.identity.contains(" · ") },
                        cell.physicalId?.let { "${cell.physicalIdLabel} $it" },
                        cell.timingAdvanceDistanceM?.let { "tower ≈ ${formatDistance(it)}" },
                    ).joinToString(" · "),
                    style = CaptionStyle.copy(fontSize = 12.sp), color = Palette.TextTertiary,
                )
            }
            Text(
                buildAnnotatedString {
                    append(cell.signalDbm?.toString()?.replace("-", "−") ?: DASH)
                    withStyle(SpanStyle(fontSize = 11.sp, color = Palette.TextTertiary)) { append(" dBm") }
                },
                style = DataStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary,
            )
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

@Composable
private fun AccessPointRow(ap: AccessPoint) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                ap.ssid ?: "hidden · ${ap.bssid}",
                style = BodyStyle.copy(fontSize = 14.sp),
                color = if (ap.ssid != null) Palette.TextPrimary else Palette.TextTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(band(ap.frequencyMhz), style = DataStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary)
            // −30 dBm is as strong as Wi-Fi gets, −90 is the edge.
            val fraction = ((ap.rssiDbm + 90) / 60f).coerceIn(0f, 1f)
            LevelBar(fraction, if (ap.rssiDbm >= -60) Palette.TextPrimary else if (ap.rssiDbm >= -75) Palette.TextSecondary else Palette.TextTertiary, Modifier.width(56.dp))
            Text(ap.rssiDbm.toString().replace("-", "−"), style = DataStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
        }
        HorizontalDivider(color = Color15)
    }
}

private val Color15 = androidx.compose.ui.graphics.Color(0xFF151515)
private const val AP_PREVIEW = 8
