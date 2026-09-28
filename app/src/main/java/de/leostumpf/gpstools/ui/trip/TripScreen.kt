// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.leostumpf.gpstools.ui.common.BackgroundModeButton
import de.leostumpf.gpstools.ui.common.DASH
import de.leostumpf.gpstools.ui.common.Glossary
import de.leostumpf.gpstools.ui.common.Note
import de.leostumpf.gpstools.ui.common.Primer
import de.leostumpf.gpstools.ui.common.SectionLabel
import de.leostumpf.gpstools.ui.common.ValueRow
import de.leostumpf.gpstools.ui.common.duration
import de.leostumpf.gpstools.ui.common.metres
import de.leostumpf.gpstools.ui.speed.formatSpeed
import de.leostumpf.gpstools.ui.theme.ErrorRed

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
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
    ) {
        item { Primer(Glossary.trip) }
        item { Spacer(Modifier.height(20.dp)) }
        item {
            Column {
                Text(
                    text = stats?.let { metres(it.distanceM, 0) } ?: "0 m",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = when {
                        state.recording -> "Recording · ${stats?.let { duration(it.durationMs) } ?: "0:00"}"
                        stats != null && stats.points > 0 -> "Paused · ${duration(stats.durationMs)}"
                        else -> "Not recording"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.recording) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onToggleRecording, modifier = Modifier.weight(1f)) {
                    Text(if (state.recording) "Pause" else if ((stats?.points ?: 0) > 0) "Resume" else "Start")
                }
                OutlinedButton(onClick = onExport, enabled = (stats?.points ?: 0) > 1, modifier = Modifier.weight(1f)) {
                    Text("Export GPX")
                }
            }
        }
        state.message?.let {
            item { Spacer(Modifier.height(6.dp)) }
            item { Note(it) }
        }

        item { Spacer(Modifier.height(24.dp)) }
        item { SectionLabel("STATISTICS") }
        item { Spacer(Modifier.height(6.dp)) }
        val unit = state.unit
        item { ValueRow("Moving time", stats?.let { duration(it.movingTimeMs) } ?: DASH) }
        item { ValueRow("Max speed", stats?.maxSpeedMps?.let { "${formatSpeed(it, unit)} ${unit.symbol}" } ?: DASH) }
        item {
            ValueRow("Average while moving", stats?.avgMovingSpeedMps?.let { "${formatSpeed(it, unit)} ${unit.symbol}" } ?: DASH)
        }
        item {
            ValueRow(
                "Ascent / descent",
                stats?.let { "↑ ${metres(it.ascentM, 0)}  ↓ ${metres(it.descentM, 0)}" } ?: DASH,
                detail = if (state.climbFromBarometer) "from the barometer" else "from GNSS heights (noisier)",
            )
        }
        item { ValueRow("Track points", stats?.points?.toString() ?: "0") }

        item { Spacer(Modifier.height(16.dp)) }
        item {
            Note(
                if (backgroundActive) {
                    "Background mode is on: recording continues while you use other apps or " +
                        "switch the screen off. The track is saved on the phone as it goes."
                } else {
                    "Records while GPS Tools is on screen (it keeps the screen awake). To keep " +
                        "recording with the screen off or in other apps, switch background mode " +
                        "on. The track is saved on the phone as it goes."
                },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        item { BackgroundModeButton(active = backgroundActive, onSetActive = onSetBackground) }
        if ((stats?.points ?: 0) > 0 && !state.recording) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                TextButton(onClick = { confirmClear = true }) { Text("Delete this trip", color = ErrorRed) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete the trip?") },
            text = { Text("The recorded track is removed from the phone. Export it first to keep it.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
