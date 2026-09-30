// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import androidx.compose.runtime.LaunchedEffect
import io.github.leostumpf.positioninginfo.ui.theme.signalColour
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.leostumpf.positioninginfo.domain.counted
import io.github.leostumpf.positioninginfo.ui.common.rememberLogTimeFormat
import java.util.Locale
import io.github.leostumpf.positioninginfo.ui.common.TileRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import io.github.leostumpf.positioninginfo.domain.History
import io.github.leostumpf.positioninginfo.domain.HistorySample
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import io.github.leostumpf.positioninginfo.domain.AcquisitionStage
import io.github.leostumpf.positioninginfo.domain.FixDiagnosis
import io.github.leostumpf.positioninginfo.ui.common.AppIcons
import io.github.leostumpf.positioninginfo.ui.common.CircleIconButton
import io.github.leostumpf.positioninginfo.ui.common.Gutter
import io.github.leostumpf.positioninginfo.ui.common.SectionHeader
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import io.github.leostumpf.positioninginfo.domain.CheckStatus
import io.github.leostumpf.positioninginfo.domain.Diagnosis
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import io.github.leostumpf.positioninginfo.domain.AlmanacReadiness
import io.github.leostumpf.positioninginfo.domain.AlmanacStatus
import io.github.leostumpf.positioninginfo.domain.ConstellationSummary
import io.github.leostumpf.positioninginfo.domain.SatelliteId
import io.github.leostumpf.positioninginfo.domain.SignalBand
import io.github.leostumpf.positioninginfo.domain.band
import io.github.leostumpf.positioninginfo.ui.common.LevelBar
import io.github.leostumpf.positioninginfo.data.model.PhoneSettings
import io.github.leostumpf.positioninginfo.data.model.SignalMeasurement
import io.github.leostumpf.positioninginfo.domain.PowerSaveLocation
import io.github.leostumpf.positioninginfo.domain.RawSignal
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.QuietButton
import io.github.leostumpf.positioninginfo.ui.common.SecondaryButton
import io.github.leostumpf.positioninginfo.ui.common.StatTile
import io.github.leostumpf.positioninginfo.ui.common.StatusBadge
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.sky.SkyUiState
import io.github.leostumpf.positioninginfo.ui.sky.label
import io.github.leostumpf.positioninginfo.ui.sky.skyEventItems
import io.github.leostumpf.positioninginfo.ui.sky.skyPlotItems
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.color
import io.github.leostumpf.positioninginfo.ui.theme.PageTitleStyle

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
    sky: SkyUiState,
    onToggleCompass: () -> Unit,
    onToggleMap: () -> Unit,
    onToggleShowPaths: () -> Unit,
    onClearPaths: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmColdStart by rememberSaveable { mutableStateOf(false) }
    var showUnheard by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
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
        state.diagnosis?.let { d -> item { DiagnosisCard(d, Modifier.padding(top = 16.dp)) } }
        item {
            TileRow(
                listOf(
                    { m -> StatTile("visible", state.visible.toString(), m) },
                    { m -> StatTile("almanac", state.withAlmanac.toString(), m) },
                    { m -> StatTile("ephemeris", if (state.ephemerisUnavailable) "—" else state.withEphemeris.toString(), m) },
                    { m -> StatTile("in fix", state.usedInFix.toString(), m, tone = if (state.usedInFix > 0) Tone.GOOD else null) },
                ),
                Modifier.padding(top = 16.dp),
            )
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

        skyPlotItems(sky, onToggleCompass, onToggleMap, onToggleShowPaths, onClearPaths)

        if (state.signals.isNotEmpty()) {
            section("Satellites · ${state.visible}", trailing = "C/N₀ · A E")
            items(heard, key = { it.key }) { SatelliteRow(it.satellite, state.details[it.baseKey]) { selectedKey = it.baseKey } }
            if (unheard.isNotEmpty()) {
                if (showUnheard) items(unheard, key = { it.key }) { SatelliteRow(it.satellite, state.details[it.baseKey]) { selectedKey = it.baseKey } }
                item {
                    QuietButton(
                        if (showUnheard) "Hide the ${unheard.size} not heard" else "Show ${unheard.size} not heard (almanac only)",
                        onClick = { showUnheard = !showUnheard },
                    )
                }
            }
            item {
                Note(
                    "Filled dot: used in the fix. Ring: heard. Faint: known only from the almanac. The four " +
                        "ticks are the acquisition steps — code lock, bit sync, frame sync, time decoded; a " +
                        "satellite is usable once all four are done. Tap a row for details.",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        skyEventItems(sky)

        section("Timing")
        item { TimingRow("Time to first fix", state.timing.firstFixText(state.gpsEnabled)) }
        item { TimingRow("Phone clock vs GNSS", state.timing.clockText()) }
        val networkVsGnss = state.timing.networkVsGnssText()
        item { TimingRow("Phone clock vs network", state.timing.networkClockText(), divider = networkVsGnss != null) }
        networkVsGnss?.let { item { TimingRow("Network time vs GNSS", it, divider = false) } }

        if (state.history.size > 1) {
            section("Last 30 minutes")
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HistoryChart("Satellites in fix", state.history, { it.usedInFix.toFloat() }, "", decimals = 0)
                    HistoryChart("Mean signal, C/N₀", state.history, { it.meanCn0 }, " dB-Hz", decimals = 0)
                    HistoryChart("Accuracy", state.history, { it.accuracyM }, " m", decimals = 1, lowerIsBetter = true)
                }
            }
        }
        if (state.ttffLog.isNotEmpty()) {
            section("Recent first fixes", trailing = "stored on this phone")
            state.ttffLog.takeLast(5).reversed().forEachIndexed { i, e ->
                item {
                    ValueRow(
                        rememberLogTimeFormat().format(java.util.Date(e.utcMs)),
                        FixDiagnosis.formatDuration(e.ttffMs),
                        // Slow is said as well as coloured, for anyone who cannot tell the two apart.
                        detail = when (e.startType) {
                            AlmanacReadiness.HOT -> "hot start"
                            AlmanacReadiness.WARM -> "warm start"
                            AlmanacReadiness.COLD -> "cold start"
                            AlmanacReadiness.UNKNOWN -> "start type unknown"
                        } + if (e.ttffMs > FixDiagnosis.expectedMs(e.startType)) " · slower than usual" else "",
                        valueColor = if (e.ttffMs <= FixDiagnosis.expectedMs(e.startType)) Palette.Good else Palette.Degraded,
                        divider = i < minOf(5, state.ttffLog.size) - 1,
                    )
                }
            }
        }

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

        section("Phone settings", trailing = "read only")
        item { PhoneSettingsRows(state.settings) }

        if (state.perConstellation.isNotEmpty()) {
            section("Constellations")
            item { ConstellationHeader() }
            items(state.perConstellation, key = { it.constellation.name }) {
                ConstellationRow(it, last = it == state.perConstellation.last())
            }
        }
    }

    selectedKey?.let { key ->
        val row = state.signals.firstOrNull { it.baseKey == key }
        // Gone from the list: close the sheet, from an effect rather than mid-composition.
        if (row == null) LaunchedEffect(key) { selectedKey = null }
        else SatelliteSheet(row, state.details[key], siblings = state.signals.filter {
            it.satellite.constellation == row.satellite.constellation && it.satellite.svid == row.satellite.svid
        }, onDismiss = { selectedKey = null })
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
private fun SatelliteRow(satellite: SatelliteInfo, detail: SignalDetail?, onClick: () -> Unit) {
    val color = satellite.constellation.color()
    val heard = satellite.cn0DbHz > 0f
    // One spoken line instead of "G07, L1, 42, A E": the letters and grey levels mean nothing aloud.
    val spoken = buildString {
        append("${satellite.code()}, ${satellite.constellation.label}")
        satellite.band?.shortLabel()?.let { append(", $it") }
        append(if (heard) ", ${satellite.cn0DbHz.roundToInt()} dB-Hz" else ", not heard")
        if (satellite.usedInFix) append(", in the fix")
        detail?.stage?.takeIf { heard }?.let { append(", ${it.label}") }
        append(if (satellite.hasAlmanac) ", almanac" else ", no almanac")
        append(if (satellite.hasEphemeris) ", ephemeris" else ", no ephemeris")
    }
    Column(
        Modifier.alpha(if (heard) 1f else 0.55f)
            .clickable(onClickLabel = "Show satellite details", onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(8.dp).then(
                    if (satellite.usedInFix) Modifier.background(color, CircleShape)
                    else Modifier.border(if (heard) 1.5.dp else 1.dp, color, CircleShape),
                ),
            )
            Text(satellite.code(), style = DataStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = color, modifier = Modifier.widthIn(min = 40.dp))
            Text(satellite.band?.shortLabel() ?: "—", style = DataStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary, modifier = Modifier.widthIn(min = 24.dp))
            StageGauge(if (heard) detail?.stage else null)
            LevelBar((satellite.cn0DbHz / GOOD_SIGNAL_DB_HZ), signalColour(satellite.cn0DbHz), Modifier.weight(1f))
            Text(if (heard) "%.0f".format(Locale.US, satellite.cn0DbHz) else "—", style = DataStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 24.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (satellite.hasAlmanac) Palette.TextPrimary else Palette.Inactive)) { append("A ") }
                    withStyle(SpanStyle(color = if (satellite.hasEphemeris) Palette.TextPrimary else Palette.Inactive)) { append("E") }
                },
                style = DataStyle.copy(fontSize = 11.sp),
                modifier = Modifier.widthIn(min = 32.dp),
            )
        }
        HorizontalDivider(color = Palette.RowDivider)
    }
}

private const val GOOD_SIGNAL_DB_HZ = 50f

/** "G07", "E24": the RINEX letter plus a two-digit number. */
private fun SatelliteInfo.code(): String = SatelliteId(constellation, svid).label()

/** "L5 / E5a / B2a" is too wide for a list row; the first name identifies the band. */
private fun SignalBand.shortLabel(): String? =
    if (this == SignalBand.UNKNOWN) null else label.substringBefore(" /")


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
    state.isFixed() -> "Fixed on ${state.usedInFix.counted("satellite")}"
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
                "${state.withEphemeris.counted("satellite")}, so after a restart it would fix again within seconds."
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

/**
 * "Why no fix?": the verdict first, then every link of the chain with its status. Open while
 * there is no fix; folded to one line once everything passes.
 */
@Composable
private fun DiagnosisCard(d: Diagnosis, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(d.fixed) { mutableStateOf(!d.fixed || d.verdictStatus != CheckStatus.OK) }
    val tone = d.verdictStatus.tone()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(if (tone == Tone.NEUTRAL) Palette.Surface else tone.color.copy(alpha = 0.07f))
            .border(1.dp, if (tone == Tone.NEUTRAL) Palette.CardBorder else tone.color.copy(alpha = 0.3f), shape)
            .clickable(onClickLabel = if (expanded) "Hide the checks" else "Show all checks") { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "expanded" else "collapsed" }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WHY NO FIX?", style = OverlineStyle, color = Palette.TextTertiary, modifier = Modifier.weight(1f))
            Text(
                if (expanded) "▾" else "▸", style = OverlineStyle, color = Palette.TextTertiary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).background(tone.color, CircleShape))
            Text(d.verdict, style = BodyStyle.copy(fontWeight = FontWeight.Medium, fontSize = 17.sp), color = Palette.TextPrimary)
        }
        Text(d.detail, style = BodyStyle.copy(fontSize = 13.sp, lineHeight = 19.sp), color = Palette.TextSecondary)
        if (expanded) {
            Column(Modifier.padding(top = 6.dp)) {
                d.checks.forEachIndexed { i, check ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(6.dp).background(check.status.tone().color, CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(check.label, style = BodyStyle.copy(fontSize = 14.sp), color = Palette.TextSecondary)
                            if (check.status != CheckStatus.OK) {
                                check.hint?.let { Text(it, style = BodyStyle.copy(fontSize = 12.sp, lineHeight = 17.sp), color = Palette.TextTertiary) }
                            }
                        }
                        Text(check.value, style = DataStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 180.dp))
                    }
                    if (i < d.checks.lastIndex) HorizontalDivider(color = Palette.Divider)
                }
            }
        }
    }
}

private fun CheckStatus.tone(): Tone = when (this) {
    CheckStatus.OK -> Tone.GOOD
    CheckStatus.WARN -> Tone.DEGRADED
    CheckStatus.FAIL -> Tone.BAD
    CheckStatus.INFO -> Tone.NEUTRAL
}

/** Four ticks, one per acquisition step; filled up to the step reached. */
@Composable
private fun StageGauge(stage: AcquisitionStage?) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(AcquisitionStage.STEPS) { i ->
            val done = stage != null && i < stage.step
            Box(
                Modifier.size(width = 4.dp, height = 10.dp).background(
                    when {
                        stage == null -> Palette.Divider
                        !done -> Palette.Hairline
                        stage == AcquisitionStage.TIME_DECODED -> Palette.Good
                        else -> Palette.TextSecondary
                    },
                    RoundedCornerShape(1.dp),
                ),
            )
        }
    }
}

/** Everything known about one satellite, updated live while open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatelliteSheet(row: SignalRow, detail: SignalDetail?, siblings: List<SignalRow>, onDismiss: () -> Unit) {
    val sat = row.satellite
    val color = sat.constellation.color()
    val heard = sat.cn0DbHz > 0f
    val stage = detail?.stage?.takeIf { heard }
    val inferred = detail?.stageInferred == true
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${SatelliteId(sat.constellation, sat.svid).label()} · ${sat.constellation.label} ${sat.svid}",
                        style = TitleStyle.copy(fontSize = 20.sp), color = color,
                    )
                    Text(
                        when {
                            sat.usedInFix -> "Used in the fix"
                            heard -> "Heard, not used in the fix"
                            else -> "Not heard — position known from the almanac"
                        },
                        style = CaptionStyle, color = Palette.TextSecondary,
                    )
                }
                CircleIconButton(AppIcons.Close, contentDescription = "Close", onClick = onDismiss)
            }

            SectionHeader("Acquisition", modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AcquisitionStage.entries.drop(1).forEach { s ->
                    val done = stage != null && s.step <= stage.step
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().height(4.dp).background(if (done) (if (stage == AcquisitionStage.TIME_DECODED) Palette.Good else Palette.TextPrimary) else Palette.Hairline, RoundedCornerShape(2.dp)))
                        Text(s.label, style = CaptionStyle.copy(fontSize = 11.sp), color = if (done) Palette.TextPrimary else Palette.TextTertiary)
                    }
                }
            }
            Note(
                when {
                    !heard -> "No signal from this satellite. The receiver only knows from the almanac that it should be up there."
                    stage == null -> "The chip reports no raw measurement for this signal right now, so how far " +
                        "acquisition has got is unknown."
                    inferred -> "In the fix, so every step is complete — the chip just reports no raw " +
                        "measurement for it right now."
                    else -> stage.meaning
                },
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionHeader("Signal", modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
            siblings.forEach { sib ->
                ValueRow(
                    "Strength" + (sib.satellite.band?.shortLabel()?.let { " · $it" } ?: ""),
                    if (sib.satellite.cn0DbHz > 0f) "%.0f dB-Hz".format(Locale.US, sib.satellite.cn0DbHz) else "—",
                )
            }
            ValueRow("Doppler shift", detail?.dopplerHz?.takeIf { heard }?.let { (if (it >= 0) "+" else "−") + String.format(java.util.Locale.US, "%.2f kHz", kotlin.math.abs(it) / 1_000) } ?: "—",
                detail = "positive: approaching")
            ValueRow("Multipath", when (detail?.multipath) { true -> "detected"; false -> "none"; null -> "not reported" })

            detail?.raw?.takeIf { heard }?.let { RawMeasurementRows(sat, it) }

            SectionHeader("Position and data", modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
            ValueRow("Elevation", "%.0f°".format(Locale.US, sat.elevationDegrees))
            ValueRow("Azimuth", "%.0f°".format(Locale.US, sat.azimuthDegrees))
            ValueRow("Almanac", if (sat.hasAlmanac) "held" else "missing")
            ValueRow("Ephemeris", if (sat.hasEphemeris) "held" else "missing")
            ValueRow(
                "First heard",
                detail?.firstHeardMs?.let { FixDiagnosis.formatDuration(android.os.SystemClock.elapsedRealtime() - it) + " ago" } ?: "—",
                divider = false,
            )
        }
    }
}

/**
 * One quantity over the last half hour: a line on a fixed time axis ending now, broken
 * where the app was in the background, with the current value and the range beside it.
 */
@Composable
private fun HistoryChart(
    label: String,
    samples: List<HistorySample>,
    value: (HistorySample) -> Float?,
    unit: String,
    decimals: Int,
    lowerIsBetter: Boolean = false,
) {
    val points = samples.mapNotNull { s -> value(s)?.let { s.atMs to it } }
    val fmt = { v: Float -> String.format(java.util.Locale.US, "%.${decimals}f", v) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = BodyStyle.copy(fontSize = 14.sp), color = Palette.TextSecondary, modifier = Modifier.weight(1f))
            Text(points.lastOrNull()?.let { fmt(it.second) + unit } ?: "—", style = DataStyle, color = Palette.TextPrimary)
        }
        Canvas(
            Modifier.fillMaxWidth().height(44.dp).semantics {
                contentDescription = "$label over the last 30 minutes" + (
                    points.takeIf { it.isNotEmpty() }?.let { p -> ", from ${fmt(p.minOf { it.second })} to ${fmt(p.maxOf { it.second })}$unit" } ?: ""
                    )
            },
        ) {
            drawLine(Palette.Hairline, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height))
            if (points.size < 2) return@Canvas
            val end = samples.last().atMs
            val start = end - History.WINDOW_MS
            val min = points.minOf { it.second }
            val max = points.maxOf { it.second }
            val span = (max - min).takeIf { it > 0f } ?: 1f
            fun x(t: Long) = ((t - start).toFloat() / History.WINDOW_MS) * size.width
            fun y(v: Float) = size.height - 3.dp.toPx() - ((v - min) / span) * (size.height - 6.dp.toPx())
            val path = androidx.compose.ui.graphics.Path()
            var previous: Long? = null
            points.forEach { (t, v) ->
                if (previous == null || t - previous > History.GAP_MS) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
                previous = t
            }
            drawPath(path, Palette.TextPrimary, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            drawCircle(Palette.TextPrimary, 2.5.dp.toPx(), androidx.compose.ui.geometry.Offset(x(points.last().first), y(points.last().second)))
        }
        Row {
            Text("30 min ago", style = CaptionStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary, modifier = Modifier.weight(1f))
            if (points.isNotEmpty()) {
                val lo = points.minOf { it.second }
                val hi = points.maxOf { it.second }
                Text(
                    (if (lowerIsBetter) "best ${fmt(lo)} · worst ${fmt(hi)}" else "range ${fmt(lo)}–${fmt(hi)}") + unit,
                    style = CaptionStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary,
                )
            }
        }
    }
}

/** The raw-measurement fields that need a word of translation, for the satellite sheet. */
@Composable
private fun RawMeasurementRows(sat: SatelliteInfo, raw: SignalMeasurement) {
    SectionHeader("Raw measurement", modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
    raw.codeType?.let { code ->
        ValueRow("Signal code", code, detail = RawSignal.codeMeaning(sat.constellation, code) ?: "RINEX code letter")
    }
    raw.basebandCn0DbHz?.let { base ->
        ValueRow(
            "Strength at the chip", String.format(java.util.Locale.US, "%.1f dB-Hz", base),
            detail = String.format(java.util.Locale.US, "%.1f dB lost between antenna and correlators", raw.cn0DbHz - base),
        )
    }
    raw.snrDb?.let { ValueRow("Signal-to-noise", String.format(java.util.Locale.US, "%.1f dB", it)) }
    raw.receivedSvTimeUncertaintyNs?.let { ns ->
        val metres = RawSignal.timeUncertaintyM(ns)
        ValueRow(
            "Time uncertainty", if (ns < 1_000_000) "± $ns ns" else String.format(java.util.Locale.US, "± %.1f ms", ns / 1e6),
            detail = if (metres < 10_000) String.format(java.util.Locale.US, "≈ %.0f m of range", metres) else String.format(java.util.Locale.US, "≈ %.0f km of range", metres / 1_000),
        )
    }
    ValueRow("Carrier phase", RawSignal.carrierPhase(raw.carrierPhaseState), divider = raw.interSignalBiasNs != null)
    raw.interSignalBiasNs?.let {
        ValueRow("Inter-signal bias", String.format(java.util.Locale.US, "%.1f ns", it), detail = "delay against the receiver's reference signal", divider = false)
    }
}

/** What the user has switched on or off around positioning; all read-only. */
@Composable
private fun PhoneSettingsRows(s: PhoneSettings) {
    fun onOff(v: Boolean?) = when (v) { true -> "on"; false -> "off"; null -> "not readable" }
    Column {
        ValueRow("Location", onOff(s.locationOn), valueColor = if (s.locationOn == false) Palette.Bad else null)
        ValueRow(
            "Location access", when (s.preciseLocation) { true -> "precise"; false -> "approximate"; null -> "—" },
            detail = "approximate access hides the GNSS receiver entirely".takeIf { s.preciseLocation == false },
            valueColor = if (s.preciseLocation == false) Palette.Degraded else null,
        )
        ValueRow("Wi-Fi & cell positioning", onOff(s.networkLocation), detail = "the network provider; faster, rougher fixes")
        ValueRow("Wi-Fi scanning", onOff(s.wifiScanning), detail = "finds access points for positioning even with Wi-Fi off")
        ValueRow("Bluetooth scanning", onOff(s.bluetoothScanning), detail = "finds beacons for positioning even with Bluetooth off")
        ValueRow(
            "Battery saver", when (s.powerSave) {
                PowerSaveLocation.UNRESTRICTED -> "no effect"
                PowerSaveLocation.GNSS_OFF_SCREEN_OFF -> "GNSS off with screen off"
                PowerSaveLocation.ALL_OFF_SCREEN_OFF -> "location off with screen off"
                PowerSaveLocation.FOREGROUND_ONLY -> "foreground apps only"
                PowerSaveLocation.THROTTLED_SCREEN_OFF -> "throttled with screen off"
            },
            valueColor = if (s.powerSave != PowerSaveLocation.UNRESTRICTED) Palette.Degraded else null,
        )
        ValueRow("Automatic time", onOff(s.autoTime), detail = "set from the network, not from GNSS")
        ValueRow("Automatic time zone", onOff(s.autoTimeZone), detail = s.timeZone?.let { "now $it" }, divider = false)
        Note("Change these in Android's settings; the app only reads them.", modifier = Modifier.padding(top = 6.dp))
    }
}
