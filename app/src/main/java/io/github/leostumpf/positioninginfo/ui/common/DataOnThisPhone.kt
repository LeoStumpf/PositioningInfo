// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.leostumpf.positioninginfo.ui.theme.Palette

/** What the app keeps, counted, so the user can see it before clearing it. */
data class DataInventory(
    val tripPoints: Int = 0,
    val firstFixEntries: Int = 0,
    val unitChanged: Boolean = false,
    val historySamples: Int = 0,
)

/**
 * Everything the app keeps, and one button to remove it. Nothing ever leaves the phone;
 * this is about being able to see and wipe what stays on it.
 */
@Composable
fun DataOnThisPhone(inventory: DataInventory, onClearAll: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column {
        Note(
            "Nothing ever leaves this phone — the app has no internet access. This is what it keeps:",
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ValueRow("Recorded trip", if (inventory.tripPoints > 0) "${inventory.tripPoints} points" else "none", detail = "on the phone")
        ValueRow("Recent first fixes", if (inventory.firstFixEntries > 0) "${inventory.firstFixEntries} entries" else "none", detail = "on the phone")
        ValueRow("Speed unit", if (inventory.unitChanged) "changed" else "default", detail = "on the phone")
        ValueRow(
            "Session data",
            if (inventory.historySamples > 0) "${inventory.historySamples} samples" else "none",
            detail = "in memory: history, sky paths, signal map, accuracy test, calibrations",
            divider = false,
        )
        QuietButton("Clear all data", onClick = { confirming = true }, destructive = true)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = Palette.Sheet,
            title = { Text("Clear all data?") },
            text = {
                Text(
                    "Deletes the recorded trip, the first-fix log and the unit setting, and resets " +
                        "everything collected this session: history, sky paths, signal map, accuracy test, " +
                        "max/avg speed and calibrations. Export the trip first to keep it.",
                    color = Palette.TextSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { confirming = false; onClearAll() }) { Text("Clear", color = Palette.Bad) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel", color = Palette.TextPrimary) } },
        )
    }
}
