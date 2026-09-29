// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.leostumpf.positioninginfo.ui.common.BackgroundModeSwitch
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.HeroValue
import io.github.leostumpf.positioninginfo.ui.common.InfoCard
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.PrimaryButton
import io.github.leostumpf.positioninginfo.ui.common.QuietButton
import io.github.leostumpf.positioninginfo.ui.common.SecondaryButton
import io.github.leostumpf.positioninginfo.ui.common.StatTile
import io.github.leostumpf.positioninginfo.ui.common.StatusBadge
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.duration
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.speed.formatSpeed
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.StatusLineStyle
import kotlin.math.roundToInt

/**
 * Records a track on the phone and exports it as GPX to a file the user picks. Nothing
 * leaves the phone unless they share that file themselves.
 */
@Composable
fun TripScreen(
    state: TripUiState,
    onToggleRecording: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    backgroundActive: Boolean,
    onSetBackground: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val stats = state.stats
    val points = stats?.points ?: 0
    val unit = state.unit

    PageScaffold(Page.TRIP, modifier) {
        item {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        state.recording -> StatusBadge("Recording", Tone.BAD)
                        points > 0 -> StatusBadge("Paused", Tone.DEGRADED)
                        else -> StatusBadge("Not recording", Tone.NEUTRAL)
                    }
                    if (points > 0) Text(duration(stats!!.durationMs), style = StatusLineStyle, color = Palette.TextSecondary)
                }
                val km = (stats?.distanceM ?: 0.0) >= 1_000
                HeroValue(
                    value = if (km) (stats!!.distanceM / 1_000).fmt(2) else (stats?.distanceM ?: 0.0).roundToInt().toString(),
                    unit = if (km) "km" else "m",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    PrimaryButton(
                        text = if (state.recording) "Pause" else if (points > 0) "Resume" else "Start recording",
                        onClick = onToggleRecording,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton("Export GPX", onClick = onExport, enabled = points > 1, modifier = Modifier.weight(1f))
                }
                state.message?.let { Note(it) }
            }
        }

        if (state.elevationProfile.size > 1) {
            section("Elevation")
            item { ElevationProfile(state.elevationProfile) }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("↑ ${stats?.ascentM?.roundToInt() ?: 0} m", style = StatusLineStyle, color = Palette.TextTertiary, modifier = Modifier.weight(1f))
                    Text(
                        "↓ ${stats?.descentM?.roundToInt() ?: 0} m · ${if (state.climbFromBarometer) "barometric" else "from GNSS"}",
                        style = StatusLineStyle, color = Palette.TextTertiary,
                    )
                }
            }
        }

        section("Statistics")
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Moving time", stats?.let { duration(it.movingTimeMs) } ?: DASH, Modifier.weight(1f))
                    StatTile("Max speed", stats?.maxSpeedMps?.let { formatSpeed(it, unit) } ?: DASH, Modifier.weight(1f), unit = unit.symbol)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Average moving", stats?.avgMovingSpeedMps?.let { formatSpeed(it, unit) } ?: DASH, Modifier.weight(1f), unit = unit.symbol)
                    StatTile("Track points", "%,d".format(points).replace(',', ' '), Modifier.weight(1f))
                }
                if (state.elevationProfile.size <= 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Ascent", "${stats?.ascentM?.roundToInt() ?: 0}", Modifier.weight(1f), unit = "m")
                        StatTile("Descent", "${stats?.descentM?.roundToInt() ?: 0}", Modifier.weight(1f), unit = "m")
                    }
                }
            }
        }

        section("Recording with the screen off")
        item { BackgroundModeSwitch(active = backgroundActive, onSetActive = onSetBackground) }
        item {
            Note(
                "Saved on the phone as it records, so a trip survives the app being closed. " +
                    "Nothing leaves the phone unless you export it.",
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (points > 0 && !state.recording) {
            item { QuietButton("Delete this trip", onClick = { confirmClear = true }, destructive = true, modifier = Modifier.padding(top = 16.dp)) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Palette.Sheet,
            title = { Text("Delete the trip?") },
            text = { Text("The recorded track is removed from the phone. Export it first to keep it.", color = Palette.TextSecondary) },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Delete", color = Palette.Bad) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = Palette.TextPrimary) } },
        )
    }
}

@Composable
private fun ElevationProfile(heights: List<Double>) {
    InfoCard(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val min = heights.min()
            val span = (heights.max() - min).coerceAtLeast(10.0)
            val step = size.width / (heights.size - 1)
            fun y(h: Double) = (size.height - ((h - min) / span * size.height)).toFloat()
            val line = Path().apply {
                heights.forEachIndexed { i, h -> if (i == 0) moveTo(0f, y(h)) else lineTo(i * step, y(h)) }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Palette.TextPrimary.copy(alpha = 0.06f))
            drawPath(line, Palette.TextPrimary, style = Stroke(1.5.dp.toPx()))
            drawCircle(Palette.TextPrimary, 2.5.dp.toPx(), Offset(size.width, y(heights.last())))
        }
    }
}
