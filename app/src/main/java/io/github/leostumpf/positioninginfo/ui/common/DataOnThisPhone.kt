// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.leostumpf.positioninginfo.domain.counted

/**
 * Everything the app keeps, and one button to remove it. Nothing ever leaves the phone;
 * this is about being able to see and wipe what stays on it.
 */
@Composable
fun DataOnThisPhone(inventory: DataInventory, onClearAll: () -> Unit, modifier: Modifier = Modifier) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Note(
            "Nothing ever leaves this phone — the app has no internet access. This is what it keeps:",
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ValueRow(
            "Recorded trip",
            if (inventory.tripPoints > 0) inventory.tripPoints.counted("point") else "none",
            detail = "on the phone",
        )
        ValueRow(
            "Recent first fixes",
            if (inventory.firstFixEntries > 0) inventory.firstFixEntries.counted("entry", "entries") else "none",
            detail = "on the phone",
        )
        ValueRow("Speed unit", if (inventory.unitChanged) "changed" else "default", detail = "on the phone")
        ValueRow(
            "Session data",
            if (inventory.historySamples > 0) inventory.historySamples.counted("sample") else "none",
            detail = "in memory: history, sky paths, signal map, accuracy test, calibrations",
            divider = false,
        )
        QuietButton("Clear all data", onClick = { confirming = true }, destructive = true)
    }
    if (confirming) {
        ConfirmDialog(
            title = "Clear all data?",
            text = "Deletes the recorded trip, the first-fix log and the unit setting, and resets " +
                "everything collected this session: history, sky paths, signal map, accuracy test, " +
                "max/avg speed and calibrations. Export the trip first to keep it.",
            confirmLabel = "Clear",
            onConfirm = onClearAll,
            onDismiss = { confirming = false },
        )
    }
}
