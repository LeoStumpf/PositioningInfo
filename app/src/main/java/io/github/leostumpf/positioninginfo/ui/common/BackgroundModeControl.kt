// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette

/**
 * Background mode: off by default, explained before it is switched on, off immediately.
 *
 * [content] receives the toggle to call; the explanation dialog and the notification
 * permission request live here so every place that offers the switch behaves the same.
 */
@Composable
fun BackgroundModeGate(
    active: Boolean,
    onSetActive: (Boolean) -> Unit,
    content: @Composable (toggle: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var explaining by rememberSaveable { mutableStateOf(false) }
    // The notification is how the user sees and stops background mode, so it is asked for
    // on Android 13+. Declining does not block the mode: the service still runs and is
    // listed in the system's active-apps panel.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onSetActive(true) }

    content { if (active) onSetActive(false) else explaining = true }

    if (explaining) {
        BackgroundModeDialog(
            onDismiss = { explaining = false },
            onConfirm = {
                explaining = false
                val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsAsk) {
                    notificationPermission.launch(
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                } else {
                    onSetActive(true)
                }
            },
        )
    }
}

/** The compact pill used in the speed page's header. */
@Composable
fun BackgroundModeButton(active: Boolean, onSetActive: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    BackgroundModeGate(active, onSetActive) { toggle ->
        OutlinedButton(
            onClick = toggle,
            modifier = modifier.heightIn(min = 48.dp).semantics { stateDescription = if (active) "on" else "off" },
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            border = BorderStroke(1.dp, if (active) Palette.Good.copy(alpha = 0.4f) else Palette.Outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (active) Palette.Good.copy(alpha = 0.10f) else Palette.Background,
                contentColor = if (active) Palette.Good else Palette.TextSecondary,
            ),
        ) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (active) Palette.Good else Palette.Background)
                    .border(1.5.dp, if (active) Palette.Good else Palette.TextTertiary, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text("Background", style = CaptionStyle)
        }
    }
}

/** The explained switch used where recording matters. */
@Composable
fun BackgroundModeSwitch(active: Boolean, onSetActive: (Boolean) -> Unit) {
    BackgroundModeGate(active, onSetActive) { toggle ->
        SwitchRow(
            title = "Background mode",
            subtitle = if (active) {
                "On — keeps running in other apps and with the screen off. A notification shows it."
            } else {
                "Off — stops when you leave the app."
            },
            checked = active,
            onCheckedChange = { toggle() },
        )
    }
}

@Composable
private fun BackgroundModeDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Sheet)
                .border(1.dp, Palette.CardBorder, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(AppIcons.Pin, contentDescription = null, tint = Palette.TextPrimary, modifier = Modifier.size(32.dp))
            Text(
                "Keep running in the background?",
                style = BodyStyle.copy(fontSize = 22.sp, lineHeight = 28.sp),
                color = Palette.TextPrimary,
            )
            Text(
                "Normally Positioning Info releases the receiver the moment you leave it — it never tracks " +
                    "you unnoticed and costs no battery while unused.",
                style = BodyStyle,
                color = Palette.TextSecondary,
            )
            Text(
                "Background mode keeps using your location with the screen off or while you are in " +
                    "other apps: for recording a trip, the accuracy test, or letting the signal map fill in.",
                style = BodyStyle,
                color = Palette.TextSecondary,
            )
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Background)
                    .border(1.dp, Palette.CardBorder, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Consequence(AppIcons.Bell, "A permanent notification shows while it's on, with a Stop button.")
                Consequence(AppIcons.Battery, "Battery use is like navigation with the screen off.")
                Consequence(AppIcons.Lock, "Nothing leaves the phone — the app has no internet access.")
                Consequence(AppIcons.Close, "Swiping the app away from recents ends it too.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton("Turn on", onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Consequence(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = Palette.TextSecondary,
            modifier = Modifier.padding(top = 1.dp).size(18.dp),
        )
        Text(text, style = CaptionStyle, color = Palette.TextPrimary)
    }
}
