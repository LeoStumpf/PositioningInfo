// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.leostumpf.gpstools.ui.theme.OkGreen
import de.leostumpf.gpstools.ui.theme.StatusLineStyle

/**
 * The one switch for background mode. Turning it on first explains what it does and costs;
 * turning it off is immediate.
 */
@Composable
fun BackgroundModeButton(
    active: Boolean,
    onSetActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var explaining by rememberSaveable { mutableStateOf(false) }
    // The notification is how the user sees and stops background mode, so it is asked for
    // on Android 13+. Declining does not block the mode: the service still runs, and it
    // stays listed under the system's active-apps panel.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onSetActive(true) }

    OutlinedButton(
        onClick = { if (active) onSetActive(false) else explaining = true },
        modifier = modifier,
    ) {
        Text(
            text = if (active) "● Background: on" else "Background: off",
            style = StatusLineStyle,
            color = if (active) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text("Keep running in the background?") },
            text = {
                Column {
                    Text(
                        "Normally GPS Tools releases the GNSS receiver the moment you leave " +
                            "it: it never tracks you unnoticed and costs no battery while unused.",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Background mode keeps everything running while you use other apps " +
                            "or the screen is off — for recording a trip, running the accuracy " +
                            "test, or letting the signal map fill in.",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Android shows a permanent notification while it is on, with a Stop button.\n" +
                            "• The receiver stays on, which costs battery much like navigation " +
                            "with the screen off.\n" +
                            "• Nothing leaves the phone — the app still has no internet access.\n" +
                            "• Swiping GPS Tools away from recent apps also ends it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        explaining = false
                        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsAsk) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSetActive(true)
                        }
                    },
                ) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { explaining = false }) { Text("Cancel") } },
        )
    }
}
