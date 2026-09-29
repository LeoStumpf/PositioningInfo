// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.ui.speed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.gpstools.domain.FixFreshness
import io.github.leostumpf.gpstools.ui.common.AppIcons
import io.github.leostumpf.gpstools.ui.common.BackgroundModeButton
import io.github.leostumpf.gpstools.ui.common.CircleIconButton
import io.github.leostumpf.gpstools.ui.common.GlossarySheet
import io.github.leostumpf.gpstools.ui.common.Gutter
import io.github.leostumpf.gpstools.ui.common.Page
import io.github.leostumpf.gpstools.ui.common.PageHeader
import io.github.leostumpf.gpstools.ui.common.StatusBadge
import io.github.leostumpf.gpstools.ui.common.Tone
import io.github.leostumpf.gpstools.ui.theme.OverlineStyle
import io.github.leostumpf.gpstools.ui.theme.Palette
import io.github.leostumpf.gpstools.ui.theme.PlexCondensed
import io.github.leostumpf.gpstools.ui.theme.PlexSans
import io.github.leostumpf.gpstools.ui.theme.ReadoutStyle
import io.github.leostumpf.gpstools.ui.theme.StatusLineStyle

/**
 * The speedometer: one huge number, glanceable at arm's length in a car mount.
 *
 * Landscape (a phone in a car mount) puts the figures beside the number rather than under
 * it; which layout is chosen follows the space actually available, not the orientation.
 */
@Composable
fun SpeedScreen(
    state: SpeedUiState,
    onCycleUnit: () -> Unit,
    onResetSession: () -> Unit,
    backgroundActive: Boolean,
    onSetBackground: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var glossaryOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(Palette.Background)) {
        if (maxWidth > maxHeight) {
            Landscape(state, onCycleUnit, onResetSession, backgroundActive, onSetBackground, { glossaryOpen = true })
        } else {
            // Sized from the height available so small screens and split-screen still fit.
            val size = (maxHeight.value * 0.19f).coerceIn(72f, 168f).sp
            Portrait(state, size, onCycleUnit, onResetSession, backgroundActive, onSetBackground, { glossaryOpen = true })
        }
    }
    if (glossaryOpen) GlossarySheet(Page.SPEED, onDismiss = { glossaryOpen = false })
}

@Composable
private fun Portrait(
    state: SpeedUiState,
    readoutSize: TextUnit,
    onCycleUnit: () -> Unit,
    onResetSession: () -> Unit,
    backgroundActive: Boolean,
    onSetBackground: (Boolean) -> Unit,
    onHelp: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(start = Gutter, end = Gutter, top = 24.dp, bottom = 64.dp)) {
        PageHeader(Page.SPEED, onHelp = onHelp) {
            BackgroundModeButton(active = backgroundActive, onSetActive = onSetBackground)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Readout(state, readoutSize, onCycleUnit)
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
    onCycleUnit: () -> Unit,
    onResetSession: () -> Unit,
    backgroundActive: Boolean,
    onSetBackground: (Boolean) -> Unit,
    onHelp: () -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Readout(state, (maxHeight.value * 0.62f).coerceIn(72f, 220f).sp, onCycleUnit, inline = true)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 24.dp).background(Palette.CardBorder))
        Column(
            Modifier.width(220.dp).fillMaxHeight().padding(start = 28.dp, top = 4.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                BackgroundModeButton(active = backgroundActive, onSetActive = onSetBackground)
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
private fun Readout(state: SpeedUiState, size: TextUnit, onCycleUnit: () -> Unit, inline: Boolean = false) {
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
        Text(state.unit.symbol, style = TextStyle(fontFamily = PlexSans, fontSize = 22.sp), color = Palette.TextSecondary)
    }
    val tapToCycle = Modifier
        .clickable(role = Role.Button, onClickLabel = "Change speed unit", onClick = onCycleUnit)
        .semantics { contentDescription = "Speed $speedText ${state.unit.symbol}" }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (inline) {
            Row(tapToCycle, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        } else {
            Column(tapToCycle, horizontalAlignment = Alignment.CenterHorizontally) { content() }
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
        Text(value, style = TextStyle(fontFamily = PlexCondensed, fontSize = 28.sp, fontFeatureSettings = "tnum"), color = Palette.TextPrimary)
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = OverlineStyle, color = Palette.TextTertiary, modifier = Modifier.weight(1f))
        Text(value, style = TextStyle(fontFamily = PlexCondensed, fontSize = 34.sp, fontFeatureSettings = "tnum"), color = Palette.TextPrimary)
    }
}

@Composable
private fun StatusLine(state: SpeedUiState, modifier: Modifier = Modifier, stacked: Boolean = false) {
    val (label, tone) = when {
        !state.gpsEnabled -> "Location off" to Tone.BAD
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
