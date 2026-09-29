// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.speed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.leostumpf.positioninginfo.domain.SpeedHistory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.domain.FixFreshness
import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import io.github.leostumpf.positioninginfo.ui.common.DataInventory
import io.github.leostumpf.positioninginfo.ui.common.DataOnThisPhone
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.SectionHeader
import io.github.leostumpf.positioninginfo.ui.common.SegmentedToggle
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import io.github.leostumpf.positioninginfo.ui.common.AppIcons
import io.github.leostumpf.positioninginfo.ui.common.BackgroundModeButton
import io.github.leostumpf.positioninginfo.ui.common.CircleIconButton
import io.github.leostumpf.positioninginfo.ui.common.GlossarySheet
import io.github.leostumpf.positioninginfo.ui.common.Gutter
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageHeader
import io.github.leostumpf.positioninginfo.ui.common.StatusBadge
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.CondensedFamily
import io.github.leostumpf.positioninginfo.ui.theme.SansFamily
import io.github.leostumpf.positioninginfo.ui.theme.ReadoutStyle
import io.github.leostumpf.positioninginfo.ui.theme.StatusLineStyle

/**
 * The speedometer: one huge number, glanceable at arm's length in a car mount.
 *
 * Landscape (a phone in a car mount) puts the figures beside the number rather than under
 * it; which layout is chosen follows the space actually available, not the orientation.
 */
@Composable
fun SpeedScreen(
    state: SpeedUiState,
    onSetUnit: (SpeedUnit) -> Unit,
    onResetSession: () -> Unit,
    backgroundActive: Boolean,
    onSetBackground: (Boolean) -> Unit,
    dataInventory: DataInventory,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var glossaryOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(Palette.Background)) {
        if (maxWidth > maxHeight) {
            Landscape(state, onResetSession, backgroundActive, onSetBackground, { glossaryOpen = true }, { settingsOpen = true })
        } else {
            // Sized from the height available so small screens and split-screen still fit.
            val size = (maxHeight.value * 0.19f).coerceIn(72f, 168f).sp
            Portrait(state, size, onResetSession, backgroundActive, onSetBackground, { glossaryOpen = true }, { settingsOpen = true })
        }
    }
    if (glossaryOpen) GlossarySheet(Page.SPEED, onDismiss = { glossaryOpen = false })
    if (settingsOpen) SettingsSheet(state.unit, onSetUnit, dataInventory, onClearAllData, onDismiss = { settingsOpen = false })
}

/** The few settings there are, out of the way until wanted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    unit: SpeedUnit,
    onSetUnit: (SpeedUnit) -> Unit,
    dataInventory: DataInventory,
    onClearAllData: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Sheet,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(start = Gutter, end = Gutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = TitleStyle.copy(fontSize = 20.sp), color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                CircleIconButton(AppIcons.Close, contentDescription = "Close", onClick = onDismiss)
            }
            SectionHeader("Speed unit", modifier = Modifier.padding(top = 8.dp))
            SegmentedToggle(
                options = SpeedUnit.entries.map { it.symbol },
                selected = unit.ordinal,
                onSelect = { onSetUnit(SpeedUnit.entries[it]) },
            )
            Note("Used for the speedometer and the trip statistics. Kilometres per hour by default.")
            SectionHeader("Data on this phone", modifier = Modifier.padding(top = 16.dp))
            DataOnThisPhone(dataInventory, onClearAll = { onClearAllData(); onDismiss() })
        }
    }
}

@Composable
private fun Portrait(
    state: SpeedUiState,
    readoutSize: TextUnit,
    onResetSession: () -> Unit,
    backgroundActive: Boolean,
    onSetBackground: (Boolean) -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(start = Gutter, end = Gutter, top = 24.dp, bottom = 64.dp)) {
        PageHeader(Page.SPEED, onHelp = onHelp) {
            BackgroundModeButton(active = backgroundActive, onSetActive = onSetBackground)
            CircleIconButton(AppIcons.Settings, contentDescription = "Settings", onClick = onSettings)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Readout(state, readoutSize)
        }
        if (state.speedHistory.size > 1) {
            SpeedChart(state, Modifier.fillMaxWidth().padding(bottom = 20.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat("MAX", formatSpeed(state.maxMps, state.unit))
            Box(Modifier.width(1.dp).height(36.dp).background(Palette.Hairline))
            Stat("AVG", formatSpeed(state.averageMps, state.unit))
            Box(Modifier.width(1.dp).height(36.dp).background(Palette.Hairline))
            CircleIconButton(AppIcons.Reset, contentDescription = "Reset max and average", onClick = onResetSession, size = 48.dp)
        }
        StatusLine(state, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun Landscape(
    state: SpeedUiState,
    onResetSession: () -> Unit,
    backgroundActive: Boolean,
    onSetBackground: (Boolean) -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Readout(state, (maxHeight.value * 0.62f).coerceIn(72f, 220f).sp, inline = true)
            }
            if (state.speedHistory.size > 1) {
                SpeedChart(state, Modifier.fillMaxWidth().padding(end = 28.dp, bottom = 8.dp), height = 52.dp)
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 24.dp).background(Palette.CardBorder))
        Column(
            Modifier.width(220.dp).fillMaxHeight().padding(start = 28.dp, top = 4.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                BackgroundModeButton(active = backgroundActive, onSetActive = onSetBackground)
                CircleIconButton(AppIcons.Settings, contentDescription = "Settings", onClick = onSettings)
                CircleIconButton(AppIcons.Help, contentDescription = "What am I looking at?", onClick = onHelp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatLine("MAX", formatSpeed(state.maxMps, state.unit))
                StatLine("AVG", formatSpeed(state.averageMps, state.unit))
                Box(Modifier.align(Alignment.End)) {
                    CircleIconButton(AppIcons.Reset, contentDescription = "Reset max and average", onClick = onResetSession)
                }
            }
            StatusLine(state, Modifier.align(Alignment.End), stacked = true)
        }
    }
}

@Composable
private fun Readout(state: SpeedUiState, size: TextUnit, inline: Boolean = false) {
    // A stale fix stays on screen but visibly recedes, so a frozen number can never be
    // mistaken for a live one.
    val alpha by animateFloatAsState(if (state.freshness == FixFreshness.STALE) 0.45f else 1f, label = "staleFade")
    val speedText = if (state.isAcquiring) NO_VALUE else formatSpeed(state.speedMps, state.unit)
    val content: @Composable () -> Unit = {
        Text(
            speedText,
            style = ReadoutStyle.copy(fontSize = size, lineHeight = size * 0.95f),
            color = Palette.TextPrimary,
            modifier = Modifier.alpha(alpha),
        )
        Column(horizontalAlignment = if (inline) Alignment.Start else Alignment.CenterHorizontally) {
            Text(state.unit.symbol, style = TextStyle(fontFamily = SansFamily, fontSize = 22.sp), color = Palette.TextSecondary)
            // The receiver's own 68 % uncertainty of the speed, so "63.4" is read as a measurement.
            state.speedAccuracyMps?.takeIf { !state.isAcquiring }?.let {
                Text("± ${formatSpeed(it, state.unit)}", style = StatusLineStyle.copy(fontSize = 15.sp), color = Palette.TextTertiary)
            }
        }
    }
    // Deliberately not tappable: in a car mount a stray touch must never change the unit.
    val readoutSemantics = Modifier.semantics(mergeDescendants = true) { contentDescription = "Speed $speedText ${state.unit.symbol}" }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (inline) {
            Row(readoutSemantics, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        } else {
            Column(readoutSemantics, horizontalAlignment = Alignment.CenterHorizontally) { content() }
        }
        if (state.isAcquiring) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (state.gpsEnabled) "Acquiring GPS signal…" else "Location is switched off",
                style = StatusLineStyle,
                color = Palette.TextSecondary,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = OverlineStyle, color = Palette.TextTertiary)
        Text(value, style = TextStyle(fontFamily = CondensedFamily, fontSize = 28.sp, fontFeatureSettings = "tnum"), color = Palette.TextPrimary)
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = OverlineStyle, color = Palette.TextTertiary, modifier = Modifier.weight(1f))
        Text(value, style = TextStyle(fontFamily = CondensedFamily, fontSize = 34.sp, fontFeatureSettings = "tnum"), color = Palette.TextPrimary)
    }
}

@Composable
private fun StatusLine(state: SpeedUiState, modifier: Modifier = Modifier, stacked: Boolean = false) {
    val (label, tone) = when {
        !state.gpsEnabled -> "Location off" to Tone.BAD
        state.isMock -> "Simulated" to Tone.BAD
        state.freshness == FixFreshness.FRESH -> "Fix" to Tone.GOOD
        state.freshness == FixFreshness.STALE -> "Stale" to Tone.DEGRADED
        else -> "No fix" to Tone.NEUTRAL
    }
    val details = listOfNotNull(
        state.horizontalAccuracyM?.takeIf { state.gpsEnabled }?.let { formatAccuracy(it) },
        "${state.satellitesUsed} / ${state.satellitesVisible} sats".takeIf { state.gpsEnabled },
    )
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge(label, tone)
            Text(details.joinToString(" · "), style = StatusLineStyle, color = Palette.TextSecondary)
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(label, tone)
            details.forEach { Text(it, style = StatusLineStyle, color = Palette.TextSecondary) }
        }
    }
}

/**
 * Speed since the last reset: a line on a time axis from the reset to now, broken where the
 * app was not running, with the maximum marked. Deliberately quiet — the number stays the
 * thing to read at a glance.
 */
@Composable
private fun SpeedChart(state: SpeedUiState, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 72.dp) {
    val samples = state.speedHistory
    val start = samples.first().atMs
    val end = samples.last().atMs
    val max = samples.maxOf { it.mps }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val span = (end - start).coerceAtLeast(1L).toFloat()
            val top = (max * 1.15f).coerceAtLeast(1f)
            fun x(t: Long) = (t - start) / span * size.width
            fun y(v: Float) = size.height - v / top * size.height
            drawLine(Palette.Hairline, Offset(0f, size.height), Offset(size.width, size.height))
            // The maximum, dashed, so the line can be read against it.
            drawLine(
                Palette.Outline, Offset(0f, y(max)), Offset(size.width, y(max)),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val line = Path()
            val fill = Path()
            var previous: Long? = null
            var segmentStartX = 0f
            samples.forEach { s ->
                val px = x(s.atMs)
                val py = y(s.mps)
                if (previous == null || s.atMs - previous!! > SpeedHistory.GAP_MS) {
                    if (previous != null) { fill.lineTo(x(previous!!), size.height); fill.lineTo(segmentStartX, size.height); fill.close() }
                    line.moveTo(px, py); fill.moveTo(px, size.height); fill.lineTo(px, py); segmentStartX = px
                } else {
                    line.lineTo(px, py); fill.lineTo(px, py)
                }
                previous = s.atMs
            }
            fill.lineTo(x(end), size.height); fill.lineTo(segmentStartX, size.height); fill.close()
            drawPath(fill, Palette.TextPrimary.copy(alpha = 0.07f))
            drawPath(line, Palette.TextSecondary, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                "since reset · ${elapsedLabel(end - start)}",
                style = StatusLineStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary, modifier = Modifier.weight(1f),
            )
            Text("max ${formatSpeed(max, state.unit)} ${state.unit.symbol}", style = StatusLineStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary)
        }
    }
}

private fun elapsedLabel(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "${ms / 1_000} s"
        min < 60 -> "$min min"
        else -> "${min / 60} h ${min % 60} min"
    }
}
