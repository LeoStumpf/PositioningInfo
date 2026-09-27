// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.leostumpf.gpstools.BuildConfig

/**
 * The app's source and licence offer.
 *
 * The AGPL requires that users be told the licence and where to get the corresponding
 * source. With no menu bar to hang an About entry from, this dialog is that notice.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit, onOpenSource: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GPS Tools") },
        text = {
            Column {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Copyright © 2026 Leo Stumpf",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Licensed under the GNU Affero General Public License, " +
                        "version 3 or later. This program comes with absolutely no warranty. " +
                        "You are free to redistribute and modify it under the terms of that licence.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Source code: $SOURCE_URL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpenSource) { Text("View source") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

const val SOURCE_URL = "https://github.com/leostumpf/GpsTools"
