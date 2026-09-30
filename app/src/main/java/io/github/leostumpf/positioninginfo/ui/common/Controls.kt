// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette

// --- Controls --------------------------------------------------------------------------

private val ButtonShape = RoundedCornerShape(12.dp)
private val ButtonHeight = 48.dp

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(ButtonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.TextPrimary,
            contentColor = Palette.Background,
            disabledContainerColor = Palette.SurfaceRaised,
            disabledContentColor = Palette.Inactive,
        ),
    ) { Text(text, style = BodyStyle.copy(fontWeight = FontWeight.Medium)) }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(ButtonHeight),
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) Palette.Outline else Palette.Hairline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Palette.TextPrimary,
            disabledContentColor = Palette.Inactive,
        ),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = BodyStyle.copy(fontWeight = FontWeight.Medium))
    }
}

/** A text-only action; red when it destroys something. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(ButtonHeight),
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 0.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) Palette.Bad else Palette.TextSecondary,
        ),
    ) { Text(text, style = BodyStyle.copy(fontWeight = FontWeight.Medium)) }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = Palette.TextSecondary,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size).border(1.dp, Palette.Outline, CircleShape),
    ) { Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp)) }
}

/** Mutually exclusive views of the same thing. */
@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceRaised).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            TextButton(
                onClick = { onSelect(index) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { this.selected = on },
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (on) Palette.TextPrimary else Color.Transparent,
                    contentColor = if (on) Palette.Background else Palette.TextSecondary,
                ),
            ) { Text(label, style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)) }
        }
    }
}

/** A setting with consequences, explained in its subtitle. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BodyStyle, color = Palette.TextPrimary)
            Text(subtitle, style = CaptionStyle, color = Palette.TextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Background,
                checkedTrackColor = Palette.Good,
                uncheckedThumbColor = Palette.TextTertiary,
                uncheckedTrackColor = Palette.SurfaceRaised,
                uncheckedBorderColor = Palette.Outline,
            ),
        )
    }
}

/** A horizontal level bar, e.g. signal strength. */
@Composable
fun LevelBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    // Decorative: the value it shows is always written next to it.
    Box(modifier.clearAndSetSemantics { }.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Divider)) {
        if (fraction > 0f) {
            Box(
                Modifier.fillMaxWidth(
                    fraction.coerceIn(0f, 1f),
                ).fillMaxHeight().background(color, RoundedCornerShape(3.dp)),
            )
        }
    }
}
