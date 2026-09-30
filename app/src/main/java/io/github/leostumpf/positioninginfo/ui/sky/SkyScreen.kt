// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.sky

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.domain.CompassTrust
import io.github.leostumpf.positioninginfo.domain.SatelliteId
import io.github.leostumpf.positioninginfo.ui.common.AppIcons
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.QuietButton
import io.github.leostumpf.positioninginfo.ui.common.SegmentedToggle
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.MonoFamily
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.StatusLineStyle
import io.github.leostumpf.positioninginfo.ui.theme.color
import kotlin.math.roundToInt

/**
 * The sky as the receiver sees it: where each satellite is, where it has been, and where it
 * seems to be heading. North is up and the centre is straight overhead; compass mode turns
 * it with the phone. Shown on the GNSS page, right above the satellite list.
 */
fun LazyListScope.skyPlotItems(
    state: SkyUiState,
    onToggleCompass: () -> Unit,
    onToggleMap: () -> Unit,
    onToggleShowPaths: () -> Unit,
    onClearPaths: () -> Unit,
) {
    section("Sky")
    item {
        // Wraps the compass button under the switch when the text is large.
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedToggle(
                options = listOf("Paths", "Signal map"),
                selected = if (state.mapMode) 1 else 0,
                onSelect = { if ((it == 1) != state.mapMode) onToggleMap() },
            )
            CompassToggle(state.compassMode, onToggleCompass)
        }
    }
    state.headingText?.let { text ->
        item {
            Text(
                text = text + if (state.compassUnreliable) " · calibrate: move the phone in a figure-eight" else "",
                style = StatusLineStyle,
                color = if (state.compassUnreliable) Palette.Degraded else Palette.TextSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    if (state.compassMode && state.magneticUt != null) {
        item { CompassTrustLine(state.magneticUt, state.compassTrust) }
    }
    item { SkyPlot(state, Modifier.padding(top = 12.dp)) }
    item { Legend(state.mapMode) }
    if (!state.mapMode) {
        item { PathControls(state.showPaths, onToggleShowPaths, onClearPaths, Modifier.padding(top = 8.dp)) }
    }
    item { Note(skyNote(state), modifier = Modifier.padding(top = 10.dp)) }
}

/** What the plot is showing and how long it needs to fill in. */
private fun skyNote(state: SkyUiState): String {
    val shows = if (state.mapMode) {
        "Average signal strength by direction from ${state.obstructionSamples} measurements. " +
            "Regions that stay weak or empty near the horizon are buildings and trees — give it half an hour."
    } else {
        "Paths build up while the app runs; projections need two minutes of history and " +
            "cannot show satellites that have not risen yet."
    }
    return shows + if (state.compassMode) " Hold the phone flat; the plot turns with it." else ""
}

/** "Show paths" switch and "Clear paths" button under the plot. */
@Composable
private fun PathControls(
    showPaths: Boolean,
    onToggleShowPaths: () -> Unit,
    onClearPaths: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .toggleable(value = showPaths, role = Role.Switch, onValueChange = { onToggleShowPaths() }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Switch(
                checked = showPaths,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Palette.Background,
                    checkedTrackColor = Palette.TextPrimary,
                    uncheckedThumbColor = Palette.TextTertiary,
                    uncheckedTrackColor = Palette.SurfaceRaised,
                    uncheckedBorderColor = Palette.Outline,
                ),
            )
            Text("Show paths", style = BodyStyle.copy(fontSize = 14.sp), color = Palette.TextSecondary)
        }
        QuietButton("Clear paths", onClick = onClearPaths)
    }
}

/** Satellites about to set, and which ones came and went. */
fun LazyListScope.skyEventItems(state: SkyUiState) {
    section("Setting soon")
    if (state.upcoming.isEmpty()) {
        item { Note("No satellite expected to set in the next 15 minutes.") }
    } else {
        items(state.upcoming, key = { it.id.label() }) {
            EventRow(it.id, null, "in ~${it.minutes.roundToInt().coerceAtLeast(1)} min", dataValue = true)
        }
    }

    section("Events")
    if (state.events.isEmpty()) {
        item { Note("Satellites appearing and disappearing will be listed here.") }
    } else {
        items(state.events, key = { it.key }) { EventRow(it.id, it.text, it.ago) }
    }
}

/** Whether the compass can be believed: measured field strength against the model's. */
@Composable
private fun CompassTrustLine(measuredUt: Double, trust: CompassTrust?) {
    val (text, color) = when (trust?.level) {
        null -> "Magnetic field ${measuredUt.roundToInt()} µT · needs a fix to compare with the model" to
            Palette.TextSecondary

        CompassTrust.Level.RELIABLE ->
            "Magnetic field ${measuredUt.roundToInt()} µT, expected ${trust.expectedUt.roundToInt()} · compass " +
                "reliable" to
                Palette.Good

        CompassTrust.Level.SUSPECT ->
            "Magnetic field ${measuredUt.roundToInt()} µT, expected ${trust.expectedUt.roundToInt()} · " +
                "something magnetic nearby, heading may be off" to
                Palette.Degraded

        CompassTrust.Level.DISTURBED ->
            "Magnetic field ${measuredUt.roundToInt()} µT, expected ${trust.expectedUt.roundToInt()} · compass " +
                "disturbed, heading is wrong" to
                Palette.Bad
    }
    Text(text, style = StatusLineStyle, color = color, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun CompassToggle(on: Boolean, onToggle: () -> Unit) {
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = if (on) "on" else "off" },
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        border = BorderStroke(1.dp, if (on) Palette.TextPrimary else Palette.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (on) Palette.TextPrimary else Palette.Background,
            contentColor = if (on) Palette.Background else Palette.TextSecondary,
        ),
    ) {
        Icon(AppIcons.Compass, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Compass", style = BodyStyle.copy(fontSize = 14.sp))
    }
}

/** "G12 · set below the horizon (4°) · 2 min ago": the code coloured by its constellation. */
@Composable
private fun EventRow(id: SatelliteId, text: String?, trailing: String, dataValue: Boolean = false) {
    val code = id.label()
    val color = id.constellation.color()
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, color = color),
                    ) { append(code) }
                    text?.let { withStyle(SpanStyle(color = Palette.TextPrimary)) { append(" $it") } }
                },
                style = BodyStyle,
                modifier = Modifier.weight(1f),
            )
            Text(
                trailing,
                style = if (dataValue) DataStyle else CaptionStyle,
                color = if (dataValue) Palette.TextPrimary else Palette.TextTertiary,
            )
        }
        HorizontalDivider(color = Palette.Divider)
    }
}
