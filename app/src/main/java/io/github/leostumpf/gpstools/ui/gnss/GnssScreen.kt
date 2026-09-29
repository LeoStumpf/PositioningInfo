// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.ui.gnss

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.gpstools.data.model.SatelliteInfo
import io.github.leostumpf.gpstools.domain.AlmanacReadiness
import io.github.leostumpf.gpstools.domain.AlmanacStatus
import io.github.leostumpf.gpstools.domain.ConstellationSummary
import io.github.leostumpf.gpstools.domain.SatelliteId
import io.github.leostumpf.gpstools.domain.SignalBand
import io.github.leostumpf.gpstools.domain.band
import io.github.leostumpf.gpstools.ui.common.LevelBar
import io.github.leostumpf.gpstools.ui.common.Note
import io.github.leostumpf.gpstools.ui.common.Notice
import io.github.leostumpf.gpstools.ui.common.Page
import io.github.leostumpf.gpstools.ui.common.PageScaffold
import io.github.leostumpf.gpstools.ui.common.QuietButton
import io.github.leostumpf.gpstools.ui.common.SecondaryButton
import io.github.leostumpf.gpstools.ui.common.StatTile
import io.github.leostumpf.gpstools.ui.common.StatusBadge
import io.github.leostumpf.gpstools.ui.common.Tone
import io.github.leostumpf.gpstools.ui.common.ValueRow
import io.github.leostumpf.gpstools.ui.common.section
import io.github.leostumpf.gpstools.ui.sky.label
import io.github.leostumpf.gpstools.ui.theme.BodyStyle
import io.github.leostumpf.gpstools.ui.theme.DataStyle
import io.github.leostumpf.gpstools.ui.theme.OverlineStyle
import io.github.leostumpf.gpstools.ui.theme.Palette
import io.github.leostumpf.gpstools.ui.theme.PlexMono
import io.github.leostumpf.gpstools.ui.theme.color
import io.github.leostumpf.gpstools.ui.theme.PageTitleStyle

/**
 * What orbital data the receiver is holding, and therefore how quickly it can fix.
 *
 * The headline is the readiness verdict rather than the raw counts, because that is the
 * question the numbers actually answer.
 */
@Composable
fun GnssScreen(
    state: GnssUiState,
    onColdStart: () -> Unit,
    onFetchAssistance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmColdStart by rememberSaveable { mutableStateOf(false) }
    var showUnheard by rememberSaveable { mutableStateOf(false) }
    val heard = state.signals.filter { it.satellite.cn0DbHz > 0f }
    val unheard = state.signals.filter { it.satellite.cn0DbHz <= 0f }

    PageScaffold(Page.GNSS, modifier) {
        item {
            val fixed = state.isFixed()
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Two separate facts: what the receiver is doing now, and how fast its next
                // start would be. Mixing them made "ready" appear even while it was fixed.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fixed) StatusBadge("Fix", Tone.GOOD) else StatusBadge("No fix", Tone.NEUTRAL)
                    StatusBadge("Next start: ${state.readiness.badge()}", state.readiness.tone())
                }
                Text(headline(state), style = PageTitleStyle.copy(fontSize = 28.sp, lineHeight = 34.sp), color = Palette.TextPrimary)
                Text(explanationFor(state), style = BodyStyle.copy(fontSize = 14.sp, lineHeight = 20.sp), color = Palette.TextSecondary)
            }
        }
        item {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("visible", state.visible.toString(), Modifier.weight(1f))
                StatTile("almanac", state.withAlmanac.toString(), Modifier.weight(1f))
                StatTile("ephemeris", if (state.ephemerisUnavailable) "—" else state.withEphemeris.toString(), Modifier.weight(1f))
                StatTile("in fix", state.usedInFix.toString(), Modifier.weight(1f), tone = if (state.usedInFix > 0) Tone.GOOD else null)
            }
        }
        if (state.ephemerisUnavailable) {
            item {
                Box(Modifier.padding(top = 12.dp)) {
                    Notice(
                        "This phone computes fixes while reporting no ephemeris at all, which cannot be " +
                            "true — its driver does not publish that flag, so the count is withheld.",
                        Tone.DEGRADED,
                    )
                }
            }
        }

        section("Timing")
        item { TimingRow("Time to first fix", state.timing.firstFixText(state.gpsEnabled)) }
        item { TimingRow("Phone clock vs GNSS", state.timing.clockText(), divider = false) }

        section("Assistance")
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Cold start", onClick = { confirmColdStart = true }, modifier = Modifier.weight(1f))
                SecondaryButton("Fetch A-GNSS", onClick = onFetchAssistance, modifier = Modifier.weight(1f))
            }
        }
        item {
            Note(
                state.assistanceMessage
                    ?: "Clear the stored orbits to watch a real cold start, or ask Android to download fresh ones.",
                color = if (state.assistanceMessage != null) Palette.TextPrimary else Palette.TextTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (state.perConstellation.isNotEmpty()) {
            section("Constellations")
            item { ConstellationHeader() }
            items(state.perConstellation, key = { it.constellation.name }) {
                ConstellationRow(it, last = it == state.perConstellation.last())
            }
        }

        if (state.signals.isNotEmpty()) {
            section("Satellites · ${state.visible}", trailing = "C/N₀ · A E")
            items(heard, key = { it.key }) { SatelliteRow(it.satellite) }
            if (unheard.isNotEmpty()) {
                if (showUnheard) items(unheard, key = { it.key }) { SatelliteRow(it.satellite) }
                item {
                    QuietButton(
                        if (showUnheard) "Hide the ${unheard.size} not heard" else "Show ${unheard.size} not heard (almanac only)",
                        onClick = { showUnheard = !showUnheard },
                    )
                }
            }
            item { Note("Filled dot: used in the fix. Ring: heard. Faint: known only from the almanac.", modifier = Modifier.padding(top = 4.dp)) }
        }

        if (state.visible == 0) {
            item {
                Box(Modifier.padding(top = 32.dp)) {
                    Notice(
                        if (!state.gpsEnabled) {
                            "Location is switched off, so the receiver is not running."
                        } else {
                            "No satellites reported yet. Indoors this is normal — GNSS signals need a clear view of the sky."
                        },
                    )
                }
            }
        }
    }

    if (confirmColdStart) {
        AlertDialog(
            onDismissRequest = { confirmColdStart = false },
            containerColor = Palette.Sheet,
            title = { Text("Clear aiding data?") },
            text = {
                Text(
                    "Deletes the receiver's stored almanac, ephemeris, position and time. The next fix " +
                        "will be slower for every app on this phone until the data is downloaded again.",
                    color = Palette.TextSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { confirmColdStart = false; onColdStart() }) { Text("Clear", color = Palette.Bad) } },
            dismissButton = { TextButton(onClick = { confirmColdStart = false }) { Text("Cancel", color = Palette.TextPrimary) } },
        )
    }
}

@Composable
private fun TimingRow(label: String, value: TimingText, divider: Boolean = true) {
    ValueRow(
        label,
        value.text,
        valueColor = when (value.tone) {
            TimingTone.GOOD -> Palette.Good
            TimingTone.PENDING, TimingTone.WARN -> Palette.Degraded
            TimingTone.NONE -> Palette.TextSecondary
        },
        divider = divider,
    )
}

private val ColumnWeights = listOf(2.4f, 1f, 1f, 1f, 1f)

@Composable
private fun ConstellationHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        listOf("SYSTEM", "VIS", "ALM", "EPH", "FIX").forEachIndexed { i, h ->
            Text(h, style = OverlineStyle.copy(fontWeight = FontWeight.Normal), color = Palette.TextTertiary,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(ColumnWeights[i]))
        }
    }
}

@Composable
private fun ConstellationRow(summary: ConstellationSummary, last: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(ColumnWeights[0]), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(summary.constellation.color(), CircleShape))
                Column {
                    Text(summary.constellation.label, style = BodyStyle, color = Palette.TextPrimary)
                    if (summary.constellation.operator.isNotEmpty()) {
                        Text(summary.constellation.operator, style = BodyStyle.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Palette.TextTertiary)
                    }
                }
            }
            listOf(summary.visible, summary.almanac, summary.ephemeris).forEachIndexed { i, v ->
                Text(v.toString(), style = DataStyle, color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(ColumnWeights[i + 1]))
            }
            Text(
                summary.usedInFix.toString(), style = DataStyle, textAlign = TextAlign.End,
                color = if (summary.usedInFix > 0) Palette.Good else Palette.TextPrimary, modifier = Modifier.weight(ColumnWeights[4]),
            )
        }
        if (!last) HorizontalDivider(color = Palette.Divider)
    }
}

@Composable
private fun SatelliteRow(satellite: SatelliteInfo) {
    val color = satellite.constellation.color()
    val heard = satellite.cn0DbHz > 0f
    Column(Modifier.alpha(if (heard) 1f else 0.55f)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(8.dp).then(
                    if (satellite.usedInFix) Modifier.background(color, CircleShape)
                    else Modifier.border(if (heard) 1.5.dp else 1.dp, color, CircleShape),
                ),
            )
            Text(satellite.code(), style = DataStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = color, modifier = Modifier.width(40.dp))
            Text(satellite.band?.shortLabel() ?: "—", style = DataStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary, modifier = Modifier.width(24.dp))
            LevelBar((satellite.cn0DbHz / GOOD_SIGNAL_DB_HZ), signalColour(satellite.cn0DbHz), Modifier.weight(1f))
            Text(if (heard) "%.0f".format(satellite.cn0DbHz) else "—", style = DataStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.width(24.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (satellite.hasAlmanac) Palette.TextPrimary else Palette.Inactive)) { append("A ") }
                    withStyle(SpanStyle(color = if (satellite.hasEphemeris) Palette.TextPrimary else Palette.Inactive)) { append("E") }
                },
                style = DataStyle.copy(fontSize = 11.sp),
                modifier = Modifier.width(32.dp),
            )
        }
        HorizontalDivider(color = Color(0xFF151515))
    }
}

private const val GOOD_SIGNAL_DB_HZ = 50f

/** "G07", "E24": the RINEX letter plus a two-digit number. */
private fun SatelliteInfo.code(): String = SatelliteId(constellation, svid).label()

/** "L5 / E5a / B2a" is too wide for a list row; the first name identifies the band. */
private fun SignalBand.shortLabel(): String? =
    if (this == SignalBand.UNKNOWN) null else label.substringBefore(" /")

private fun signalColour(cn0DbHz: Float): Color = when {
    cn0DbHz >= 30f -> Palette.Good
    cn0DbHz >= 20f -> Palette.Degraded
    else -> Palette.Bad
}

private fun AlmanacReadiness.badge(): String = when (this) {
    AlmanacReadiness.HOT -> "Hot start"
    AlmanacReadiness.WARM -> "Warm start"
    AlmanacReadiness.COLD -> "Cold start"
    AlmanacReadiness.UNKNOWN -> "Waiting"
}

private fun AlmanacReadiness.tone(): Tone = when (this) {
    AlmanacReadiness.HOT -> Tone.GOOD
    AlmanacReadiness.WARM -> Tone.DEGRADED
    AlmanacReadiness.COLD -> Tone.BAD
    AlmanacReadiness.UNKNOWN -> Tone.NEUTRAL
}

/** Fixed means a position is being computed right now: four satellites are the minimum. */
private fun GnssUiState.isFixed() = gpsEnabled && usedInFix >= AlmanacStatus.SATELLITES_FOR_FIX

private fun headline(state: GnssUiState): String = when {
    state.isFixed() -> "Fixed on ${state.usedInFix} satellites"
    state.readiness == AlmanacReadiness.HOT -> "Ready to fix"
    state.readiness == AlmanacReadiness.WARM -> "Almost ready"
    state.readiness == AlmanacReadiness.COLD -> "Searching blind"
    else -> "Waiting for the receiver"
}

/**
 * Says why the receiver is in the state it is. "Ready" is reached by two routes — fixing
 * right now, or holding enough precise orbits to fix shortly — and quoting the ephemeris
 * count for a device that got there by fixing would contradict the figure beside it.
 */
private fun explanationFor(state: GnssUiState): String = when {
    state.isFixed() -> when {
        state.ephemerisUnavailable -> "The receiver is computing a position right now."
        state.readiness == AlmanacReadiness.HOT ->
            "The receiver is computing a position right now. It also holds precise orbits for " +
                "${state.withEphemeris} satellites, so after a restart it would fix again within seconds."
        state.readiness == AlmanacReadiness.WARM ->
            "The receiver is computing a position right now, but holds precise orbits for only a " +
                "few satellites; a restart would take about half a minute."
        else ->
            "The receiver is computing a position right now."
    }
    else -> when (state.readiness) {
        AlmanacReadiness.HOT ->
            "Precise orbits (ephemeris) are held for at least ${AlmanacStatus.SATELLITES_FOR_FIX} satellites. " +
                "A fix should follow within seconds of hearing them."
        AlmanacReadiness.WARM ->
            "Coarse orbits (almanac) are held, but not enough precise ones. The receiver knows where to " +
                "look and needs about half a minute."
        AlmanacReadiness.COLD ->
            "Too little orbital data to fix quickly. The receiver must search blindly; a full almanac " +
                "takes up to 12 minutes."
        AlmanacReadiness.UNKNOWN -> "No report from the GNSS receiver yet."
    }
}
