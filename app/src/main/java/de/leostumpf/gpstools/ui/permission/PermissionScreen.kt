// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Why the app cannot show a speed yet, and the one action that fixes it. */
enum class PermissionState {
    /** Never asked, or asked and dismissed without a decision. */
    NEEDS_REQUEST,

    /** Denied once; the system will still show the dialog again. */
    DENIED,

    /** Denied permanently, or only coarse location granted — both require app settings. */
    NEEDS_SETTINGS,
}

@Composable
fun PermissionScreen(
    state: PermissionState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Location access needed",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = state.explanation(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
        )
        Button(onClick = if (state == PermissionState.NEEDS_SETTINGS) onOpenSettings else onRequest) {
            Text(if (state == PermissionState.NEEDS_SETTINGS) "Open app settings" else "Grant access")
        }
    }
}

private fun PermissionState.explanation(): String = when (this) {
    PermissionState.NEEDS_REQUEST ->
        "GPS Tools reads your speed directly from the GNSS receiver. " +
            "Location is used only while the app is open — it is never stored or sent anywhere."

    PermissionState.DENIED ->
        "Without location access there is no speed to show. " +
            "Location is used only while the app is open — it is never stored or sent anywhere."

    PermissionState.NEEDS_SETTINGS ->
        "Precise location must be enabled in the system settings. " +
            "Approximate location cannot provide speed."
}
